package com.createnewera.block.control;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A setting from 0 to 15 the player picks: the knob clicks round a step at a time (sneak to
 * turn it back), the slider jumps to where it's clicked.
 */
public class PanelDialBlock extends PanelControlBlock {
    public static final IntegerProperty VALUE = IntegerProperty.create("value", 0, 15);

    public enum Style {
        KNOB, SLIDER
    }

    /** The slider's track, in the model's pixels along x. */
    private static final double TRACK_FROM = 2.5, TRACK_TO = 13.5;

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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int value = state.getValue(VALUE);
        int now = switch (style) {
            case KNOB -> Mth.clamp(value + (player.isShiftKeyDown() ? -1 : 1), 0, 15);
            case SLIDER -> {
                double along = (local(state, pos, hit.getLocation())[0] - TRACK_FROM) / (TRACK_TO - TRACK_FROM);
                yield Mth.clamp((int) Math.round(along * 15), 0, 15);
            }
        };
        set(level, pos, now);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void set(Level level, BlockPos pos, int value) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(VALUE) == value)
            return;
        level.setBlock(pos, state.setValue(VALUE, value), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.2f + value / 30f);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("getValue", "", "number 0-15", args -> level.getBlockState(pos).getValue(VALUE));
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
