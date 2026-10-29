package com.createnewera.bus;

import java.util.List;
import java.util.function.Function;

import com.createnewera.registry.ModItems;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
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
    /** The client's answer to whether a part has a cable plugged in, set like {@link #clientParts}. */
    public static java.util.function.LongPredicate clientCabled = id -> false;

    public static void register(IEventBus gameBus) {
        gameBus.addListener(BusEvents::wrench);
        gameBus.addListener(BusEvents::punch);
        gameBus.addListener(BusEvents::blockBroken);
        gameBus.addListener(BusEvents::chunkSent);
    }

    /**
     * Wrench on a port turns it between input and output; sneaking takes the part off and hands
     * it back. A computer's own ports turn too, but stay on: sneaking there picks up the computer,
     * as Create does.
     */
    private static void wrench(PlayerInteractEvent.RightClickBlock event) {
        if (!AllItems.WRENCH.isIn(event.getItemStack()) || event.getHitVec() == null)
            return;
        BlockPos pos = event.getPos();
        List<BusPort> parts = event.getLevel() instanceof ServerLevel server
                ? BusData.get(server).portsAt(pos)
                : clientParts.apply(pos);
        BusPort part = PartGeometry.partAt(parts, event.getFace(), event.getHitVec().getLocation());
        boolean builtIn = part != null && Bus.builtIn(event.getLevel().getBlockState(pos), part);
        if (part == null || (builtIn && event.getEntity().isShiftKeyDown()))
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
            PortMode now = part.mode().flipped();
            server.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.5f, now == PortMode.OUT ? 1.6f : 1.2f);
            event.getEntity().displayClientMessage(Component.translatable(now == PortMode.OUT
                    ? "create_new_era.bus.port_now_out" : "create_new_era.bus.port_now_in"), true);
        }
    }

    /**
     * Hitting a part knocks off just the part, not the block it's on. The event has no hit
     * point, so it's found again from where the player is looking.
     */
    private static void punch(PlayerInteractEvent.LeftClickBlock event) {
        var player = event.getEntity();
        if (!(player.pick(player.blockInteractionRange(), 1, false) instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(event.getPos()))
            return;
        List<BusPort> parts = event.getLevel() instanceof ServerLevel server
                ? BusData.get(server).portsAt(event.getPos())
                : clientParts.apply(event.getPos());
        BusPort part = PartGeometry.partAt(parts, hit.getDirection(), hit.getLocation());
        // hitting a computer's own port is hitting the computer
        if (part == null || Bus.builtIn(event.getLevel().getBlockState(event.getPos()), part))
            return;
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getLevel() instanceof ServerLevel server))
            return;
        Bus.removePart(server, part);
        if (!player.getAbilities().instabuild)
            player.getInventory().placeItemBackInInventory(item(part));
        server.playSound(null, event.getPos(), SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 0.6f, 1.4f);
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
                if (!Bus.builtIn(event.getState(), part))
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
            case SPLICE -> ModItems.CABLE_SPLICE.asStack();
            case BRASS_PORT -> ModItems.BRASS_PORT.asStack();
            case CONTROL, HUB -> ItemStack.EMPTY;
        };
    }

    private BusEvents() {}
}
