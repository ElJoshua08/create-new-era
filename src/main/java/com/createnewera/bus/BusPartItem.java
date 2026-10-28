package com.createnewera.bus;

import com.createnewera.computer.BrassComputerBlock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Sticks its mini-block on the clicked face, centred on the click. */
public class BusPartItem extends Item {
    private final BusPort.Kind kind;

    public BusPartItem(Properties properties, BusPort.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    // before the block gets the click, so a port goes on a chest instead of opening it
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.SUCCESS;
        int[] spot = PartGeometry.spotAt(context.getClickedPos(), context.getClickedFace(), context.getClickLocation(), kind.size);
        BusPort part = Bus.placePart(level, context.getClickedPos(), context.getClickedFace(), spot[0], spot[1], kind, PortMode.OUT);
        Player player = context.getPlayer();
        if (part == null) {
            if (player != null) {
                boolean computer = level.getBlockState(context.getClickedPos()).getBlock() instanceof BrassComputerBlock;
                String why = computer ? Bus.refusalOnComputer(level, context.getClickedPos(), kind)
                        : kind == BusPort.Kind.EXPANSION ? "create_new_era.bus.expansion_needs_computer" : "create_new_era.bus.no_room";
                player.displayClientMessage(Component.translatable(why), true);
            }
            return InteractionResult.FAIL;
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
        if (player == null || !player.getAbilities().instabuild)
            stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
