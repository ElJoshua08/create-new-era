package com.createnewera.block.control;

import java.util.Map;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A needle a program moves across its dial, from empty (0) to full (1), in sixteen steps. */
public class PanelGaugeBlock extends PanelControlBlock {
    public static final IntegerProperty NEEDLE = IntegerProperty.create("needle", 0, 15);

    public PanelGaugeBlock(Properties properties, DirectionalShape shape) {
        super(properties, shape);
        registerDefaultState(defaultBlockState().setValue(NEEDLE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NEEDLE);
    }

    @Override
    public void methods(Map<String, Method> out, ServerLevel level, BlockPos pos) {
        out.put("get", args -> level.getBlockState(pos).getValue(NEEDLE) / 15.0);
        out.put("set", args -> {
            double fraction = Mth.clamp(args.arg(1).checkDouble(), 0, 1);
            int needle = (int) Math.round(fraction * 15);
            BlockState state = level.getBlockState(pos);
            if (state.getValue(NEEDLE) != needle)
                level.setBlock(pos, state.setValue(NEEDLE, needle), Block.UPDATE_ALL);
            return null;
        });
    }
}
