package com.createnewera.block.control;

import java.util.List;

import com.createnewera.bus.BusEvents;
import com.createnewera.bus.BusPort;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Any panel control. Its state lives in the block state; this is here for the client: what
 * engineer's (or aviator's) goggles show about it, the knob and slider drawn moving smoothly
 * while they're dragged, and lamps and nixies drawn glowing.
 */
public class PanelControlBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    /** Client only: where a knob or slider is drawn, 0 to 1, easing towards its value */
    public float shown = -1;
    /** Client only: the value the player is dragging it to, 0 to 1, or -1 when nobody is */
    public float dragged = -1;
    /** Client only: when it was last drawn, for easing by the time that passed */
    public long drawnAt;

    public PanelControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Where a knob or slider should be, 0 to 1: the drag in progress, or else its value. */
    public float target() {
        return dragged >= 0 ? dragged : getBlockState().getValue(PanelDialBlock.VALUE) / 15f;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof PanelControlBlock control))
            return false;
        tooltip.add(Component.literal("    ").append(state.getBlock().getName()));
        tooltip.add(Component.literal("    ").append(control.goggleValue(state)).withStyle(ChatFormatting.GRAY));
        boolean cabled = level != null && BusEvents.clientParts.apply(worldPosition).stream()
                .anyMatch(p -> p.kind() == BusPort.Kind.CONTROL && BusEvents.clientCabled.test(p.id()));
        tooltip.add(Component.literal("    ").append(Component.translatable(cabled
                ? "create_new_era.goggles.cabled" : "create_new_era.goggles.not_cabled"))
                .withStyle(cabled ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));
        return true;
    }
}
