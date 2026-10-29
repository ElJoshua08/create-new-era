package com.createnewera.block;

import com.createnewera.bus.Bus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A brass block with four data sockets on every face, all joined inside it: run every cable of a
 * network into one, and keep a factory's wiring in order.
 */
public class PortHubBlock extends Block {
    public PortHubBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel server && !oldState.is(this))
            Bus.fitHub(server, pos);
    }
}
