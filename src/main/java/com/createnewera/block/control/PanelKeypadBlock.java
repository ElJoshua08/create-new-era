package com.createnewera.block.control;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Twelve keys, 1 to 9 with * 0 # under them. It remembers the last key pressed; a program
 * hears every press through onKey.
 */
public class PanelKeypadBlock extends PanelControlBlock {
    public static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
    /** index into {@link #KEYS}, or {@link #NONE} before the first press */
    public static final IntegerProperty KEY = IntegerProperty.create("key", 0, 12);
    public static final int NONE = 12;
    /** flips on every press, so pressing the same key twice still counts as a change */
    public static final BooleanProperty STROKE = BooleanProperty.create("stroke");

    /** The keys' area on the model, in pixels: x across three columns, z down four rows. */
    private static final double X0 = 3, X1 = 13, Z0 = 2, Z1 = 14;

    public PanelKeypadBlock(Properties properties, DirectionalShape shape) {
        super(properties, shape);
        registerDefaultState(defaultBlockState().setValue(KEY, NONE).setValue(STROKE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KEY, STROKE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        double[] at = local(state, pos, hit.getLocation());
        int column = (int) ((at[0] - X0) / ((X1 - X0) / 3)), row = (int) ((at[2] - Z0) / ((Z1 - Z0) / 4));
        if (column < 0 || column > 2 || row < 0 || row > 3)
            return InteractionResult.PASS;
        level.setBlock(pos, state.setValue(KEY, row * 3 + column).cycle(STROKE), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.6f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("getKey", "", "\"0\"-\"9\" | \"*\" | \"#\" | nil", args -> {
            int key = level.getBlockState(pos).getValue(KEY);
            return key == NONE ? null : KEYS[key];
        });
    }

    @Override
    public Object value(BlockState state) {
        int key = state.getValue(KEY);
        return key == NONE ? null : KEYS[key];
    }

    @Override
    public String valueType() {
        return "\"0\"-\"9\" | \"*\" | \"#\" | nil";
    }
}
