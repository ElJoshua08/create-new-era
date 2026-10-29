package com.createnewera.block.control;

import java.util.Map;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Pressed, it stays down for half a second, like a stone button. */
public class PanelButtonBlock extends PanelControlBlock {
    public static final BooleanProperty PRESSED = BlockStateProperties.POWERED;
    private static final int HELD_TICKS = 10;

    public PanelButtonBlock(Properties properties, DirectionalShape shape) {
        super(properties, shape);
        registerDefaultState(defaultBlockState().setValue(PRESSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PRESSED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PRESSED))
            return InteractionResult.CONSUME;
        level.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, HELD_TICKS);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 0.8f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PRESSED))
            return;
        level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.5f, 0.7f);
    }

    @Override
    public void methods(Map<String, Method> out, ServerLevel level, BlockPos pos) {
        out.put("isPressed", args -> level.getBlockState(pos).getValue(PRESSED));
    }
}
