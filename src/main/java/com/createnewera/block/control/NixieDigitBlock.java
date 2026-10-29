package com.createnewera.block.control;

import org.squiddev.cobalt.LuaError;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** One glowing digit a program sets; dark until it does. */
public class NixieDigitBlock extends PanelControlBlock {
    /** 0 to 9, or {@link #DARK} */
    public static final IntegerProperty DIGIT = IntegerProperty.create("digit", 0, 10);
    public static final int DARK = 10;

    public NixieDigitBlock(Properties properties, DirectionalShape shape) {
        super(properties.lightLevel(s -> s.getValue(DIGIT) == DARK ? 0 : 11), shape);
        registerDefaultState(defaultBlockState().setValue(DIGIT, DARK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DIGIT);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("getMinValue", "", "number", args -> 0);
        api.read("getMaxValue", "", "number", args -> 9);
        api.read("getDigit", "", "number 0-9 | nil", args -> {
            int digit = level.getBlockState(pos).getValue(DIGIT);
            return digit == DARK ? null : digit;
        });
        api.order("setDigit", "digit: 0-9 | nil", "", args -> {
            int digit = args.arg(1).isNil() ? DARK : args.arg(1).checkInteger();
            if (digit < 0 || digit > 9 && digit != DARK)
                throw new LuaError("bad argument #1 to 'setDigit' (a digit 0-9 or nil expected, got " + digit + ")");
            BlockState state = level.getBlockState(pos);
            if (state.getValue(DIGIT) != digit)
                level.setBlock(pos, state.setValue(DIGIT, digit), Block.UPDATE_ALL);
            return null;
        });
    }

    @Override
    public Object value(BlockState state) {
        int digit = state.getValue(DIGIT);
        return digit == DARK ? null : digit;
    }

    @Override
    public String valueType() {
        return "number 0-9 | nil";
    }

    @Override
    public Component goggleValue(BlockState state) {
        return state.getValue(DIGIT) == DARK ? Component.translatable("create_new_era.goggles.dark") : Component.translatable("create_new_era.goggles.digit", state.getValue(DIGIT));
    }
}
