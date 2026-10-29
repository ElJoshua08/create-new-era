package com.createnewera.block.control;

import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.Bus;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A control for a hand-built panel: it sits on an andesite plate on any face, takes a data cable
 * straight into that plate, and is a device on the bus with its own functions.
 */
public abstract class PanelControlBlock extends DirectionalPartBlock {
    protected PanelControlBlock(Properties properties, DirectionalShape shape) {
        super(properties, shape, Placement.AGAINST_FACE);
    }

    /**
     * What a program can do with it. Every control is both read and given orders through its
     * own connection, so there's no output or input port to pick.
     */
    public abstract void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos);

    /** Its state as one value: what read() returns and what onChange hands its function. */
    public abstract Object value(BlockState state);

    /** What {@link #value} is, for the editor: "number 0-15", "boolean"... */
    public abstract String valueType();

    /** Everything about it a program could notice changing: its block state says it all. */
    public Object snapshot(BlockState state) {
        return state;
    }

    /** Where a click landed, in the pixels of the model as authored (facing up, plate at y 0 to 2). */
    protected static double[] local(BlockState state, BlockPos pos, Vec3 hit) {
        return DirectionalShape.unmap((hit.x - pos.getX()) * 16, (hit.y - pos.getY()) * 16, (hit.z - pos.getZ()) * 16,
                state.getValue(FACING));
    }

    /** It comes with its own connection for a cable, like the computer's ports. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel server && !oldState.is(this))
            Bus.fitControl(server, pos, state.getValue(FACING));
    }
}
