package com.createnewera.block.control;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;
import com.createnewera.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A setting from 0 to 15 the player picks by holding use on it and dragging the mouse sideways,
 * like Aeronautics' throttle lever (see DialDrag); the knob's cap turns and the slider's handle
 * slides as they go.
 */
public class PanelDialBlock extends PanelControlBlock implements EntityBlock {
    public static final IntegerProperty VALUE = IntegerProperty.create("value", 0, 15);

    public enum Style {
        KNOB, SLIDER
    }

    private final Style style;

    public PanelDialBlock(Properties properties, DirectionalShape shape, Style style) {
        super(properties, shape);
        this.style = style;
        registerDefaultState(defaultBlockState().setValue(VALUE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VALUE);
    }

    public Style style() {
        return style;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PanelDialBlockEntity(ModBlockEntities.PANEL_DIAL.get(), pos, state);
    }

    /** Sets it from a player's drag, or a program. */
    public void set(Level level, BlockPos pos, int value) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || state.getValue(VALUE) == value)
            return;
        level.setBlock(pos, state.setValue(VALUE, value), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.2f, 1.2f + value / 30f);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("getValue", "", "number 0-15", args -> level.getBlockState(pos).getValue(VALUE));
        api.read("getMinValue", "", "number", args -> 0);
        api.read("getMaxValue", "", "number", args -> 15);
        api.order("setValue", "value: 0-15", "", args -> {
            set(level, pos, Mth.clamp(args.arg(1).checkInteger(), 0, 15));
            return null;
        });
    }

    @Override
    public Object value(BlockState state) {
        return state.getValue(VALUE);
    }

    @Override
    public String valueType() {
        return "number 0-15";
    }
}
