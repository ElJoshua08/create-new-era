package com.createnewera.block.screen;

import com.createnewera.block.BrassScreenBlock;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The brass casing round a screen's back and sides runs on from one screen to the next, the way
 * Create's casings do, so a wall of screens reads as one cabinet. Only screens that face the same
 * way and so share a pane: one turned another way keeps its own frame.
 */
public class ScreenCasingCT extends SimpleCTBehaviour {
    public ScreenCasingCT() {
        super(AllSpriteShifts.BRASS_CASING);
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos, Direction face) {
        return other.getBlock() instanceof BrassScreenBlock
                && other.getValue(BrassScreenBlock.FACING) == state.getValue(BrassScreenBlock.FACING)
                && super.connectsTo(state, other, reader, pos, otherPos, face);
    }
}
