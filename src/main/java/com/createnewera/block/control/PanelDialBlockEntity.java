package com.createnewera.block.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A knob or slider. Its value lives in the block state; this is only here so the client can draw
 * the cap turning (or the handle sliding) smoothly, following the mouse while it's dragged.
 */
public class PanelDialBlockEntity extends BlockEntity {
    /** Client only: where it's drawn, 0 to 1, easing towards its value */
    public float shown = -1;
    /** Client only: the value the player is dragging it to, 0 to 1, or -1 when nobody is */
    public float dragged = -1;
    /** Client only: when it was last drawn, for easing by the time that passed */
    public long drawnAt;

    public PanelDialBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Where it should be, 0 to 1: the drag in progress, or else its value. */
    public float target() {
        return dragged >= 0 ? dragged : getBlockState().getValue(PanelDialBlock.VALUE) / 15f;
    }
}
