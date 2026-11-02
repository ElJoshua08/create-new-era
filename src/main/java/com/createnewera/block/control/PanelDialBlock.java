package com.createnewera.block.control;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import org.squiddev.cobalt.LuaError;

/**
 * A setting, 0 to 15 unless a program gives it its own range (setRange), the player picks by holding use on it and dragging the mouse sideways,
 * like Aeronautics' throttle lever (see DialDrag); the knob's cap turns and the slider's handle
 * slides as they go.
 */
public class PanelDialBlock extends PanelControlBlock {
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

    /** The most notches a range gets: finer ranges round to this many, so dragging still works */
    public static final int MAX_NOTCHES = 1000;

    /** Moves it to a notch of its range, from a player's drag or a program. */
    public void set(Level level, BlockPos pos, int notch) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || !(level.getBlockEntity(pos) instanceof PanelControlBlockEntity dial))
            return;
        notch = Mth.clamp(notch, 0, dial.notches());
        if (notch == dial.position() && dial.position >= 0)
            return;
        dial.position = notch;
        dial.setChanged();
        // the block state keeps 16 steps of it, for anything reading the block alone
        int shown = Math.round(15f * notch / dial.notches());
        if (state.getValue(VALUE) != shown)
            level.setBlock(pos, state.setValue(VALUE, shown), Block.UPDATE_ALL);
        else
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.2f, 1.2f + shown / 30f);
    }

    private static PanelControlBlockEntity dial(Level level, BlockPos pos) throws LuaError {
        if (level.getBlockEntity(pos) instanceof PanelControlBlockEntity dial)
            return dial;
        throw new LuaError("the control is gone");
    }

    /** A whole number as one, so print shows 5 and not 5.0. */
    private static Object number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? (Object) (long) v : (Object) v;
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("getValue", "", "number", args -> number(dial(level, pos).value()));
        api.read("getMinValue", "", "number", args -> number(dial(level, pos).min));
        api.read("getMaxValue", "", "number", args -> number(dial(level, pos).max));
        api.read("getStep", "", "number", args -> number(dial(level, pos).step));
        api.read("getFraction", "", "number 0-1", args -> (double) dial(level, pos).position() / dial(level, pos).notches());
        api.order("setValue", "value: number", "", args -> {
            PanelControlBlockEntity dial = dial(level, pos);
            double v = Mth.clamp(args.arg(1).checkDouble(), Math.min(dial.min, dial.max), Math.max(dial.min, dial.max));
            set(level, pos, (int) Math.round((v - dial.min) / (dial.max - dial.min) * dial.notches()));
            return null;
        });
        api.order("setRange", "min: number, max: number, step?: number", "", args -> {
            double min = args.arg(1).checkDouble(), max = args.arg(2).checkDouble();
            double step = args.arg(3).isNil() ? 1 : args.arg(3).checkDouble();
            if (!(max > min))
                throw new LuaError("bad argument #2 to 'setRange' (max must be more than min)");
            if (!(step > 0) || step > max - min)
                throw new LuaError("bad argument #3 to 'setRange' (step must be more than 0 and at most max - min)");
            PanelControlBlockEntity dial = dial(level, pos);
            // it keeps its value as near as the new range allows
            double was = dial.value();
            dial.min = min;
            dial.max = max;
            dial.step = step;
            dial.position = -2; // forces set() to store the new notch even if the number is the same
            set(level, pos, (int) Math.round((Mth.clamp(was, min, max) - min) / (max - min) * dial.notches()));
            return null;
        });
    }

    @Override
    public Object value(BlockState state) {
        return state.getValue(VALUE);
    }

    @Override
    public Object value(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PanelControlBlockEntity dial ? number(dial.value()) : value(level.getBlockState(pos));
    }

    @Override
    public Object snapshot(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PanelControlBlockEntity dial))
            return snapshot(level.getBlockState(pos));
        return java.util.List.of(dial.position(), dial.min, dial.max, dial.step);
    }

    @Override
    public String valueType() {
        return "number";
    }

    @Override
    public Component goggleValue(BlockState state) {
        return Component.translatable("create_new_era.goggles.value", state.getValue(VALUE), 15);
    }
}
