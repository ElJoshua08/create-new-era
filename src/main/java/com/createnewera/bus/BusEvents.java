package com.createnewera.bus;

import java.util.List;
import java.util.function.Function;

import com.createnewera.registry.ModItems;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

/** How players touch the parts: the wrench on them, breaking what they sit on, seeing them. */
public final class BusEvents {
    /**
     * The client's copy of the parts on a block, set by the client at start-up so this common
     * class never loads client code. Empty on servers.
     */
    public static Function<BlockPos, List<BusPort>> clientParts = pos -> List.of();

    public static void register(IEventBus gameBus) {
        gameBus.addListener(BusEvents::wrench);
        gameBus.addListener(BusEvents::blockBroken);
        gameBus.addListener(BusEvents::chunkSent);
    }

    /** Wrench on a port turns it around; sneaking takes the part off and hands it back. */
    private static void wrench(PlayerInteractEvent.RightClickBlock event) {
        if (!AllItems.WRENCH.isIn(event.getItemStack()) || event.getHitVec() == null)
            return;
        BlockPos pos = event.getPos();
        List<BusPort> parts = event.getLevel() instanceof ServerLevel server
                ? BusData.get(server).portsAt(pos)
                : clientParts.apply(pos);
        BusPort part = PartGeometry.partAt(parts, event.getFace(), event.getHitVec().getLocation());
        if (part == null)
            return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel server))
            return;
        if (event.getEntity().isShiftKeyDown()) {
            Bus.removePart(server, part);
            if (!event.getEntity().getAbilities().instabuild)
                event.getEntity().getInventory().placeItemBackInInventory(item(part));
            server.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 0.6f, 1.4f);
        } else if (part.kind() == BusPort.Kind.PORT) {
            Bus.flip(server, part);
            server.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.5f, part.mode() == PortMode.IN ? 1.6f : 1.2f);
        }
    }

    /** The parts fall off with the block they were stuck to. */
    private static void blockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server))
            return;
        List<BusPort> parts = BusData.get(server).portsAt(event.getPos());
        if (parts.isEmpty())
            return;
        if (!event.getPlayer().getAbilities().instabuild)
            for (BusPort part : parts)
                Block.popResource(server, event.getPos(), item(part));
        Bus.blockRemoved(server, event.getPos());
    }

    private static void chunkSent(ChunkWatchEvent.Sent event) {
        Bus.sendChunk(event.getPlayer(), event.getLevel(), event.getPos());
    }

    private static ItemStack item(BusPort part) {
        return switch (part.kind()) {
            case PORT -> ModItems.BUS_PORT.asStack();
            case EXPANSION -> ModItems.PORT_EXPANSION.asStack();
        };
    }

    private BusEvents() {}
}
