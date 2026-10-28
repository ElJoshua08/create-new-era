package com.createnewera.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Screens facing the same way join with their neighbours into one pane. Each side remembers
 * whether it's joined so the model can drop the brass frame there. Left and right are as seen
 * by someone looking at the front.
 */
public class BrassScreenBlock extends HorizontalMachineBlock {
    public static final MapCodec<BrassScreenBlock> CODEC = simpleCodec(BrassScreenBlock::new);

    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public BrassScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UP, DOWN, LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : connect(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connect(state, level, pos);
    }

    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state
                .setValue(UP, joins(level, pos, Direction.UP, facing))
                .setValue(DOWN, joins(level, pos, Direction.DOWN, facing))
                .setValue(LEFT, joins(level, pos, left(facing), facing))
                .setValue(RIGHT, joins(level, pos, left(facing).getOpposite(), facing));
    }

    private boolean joins(BlockGetter level, BlockPos pos, Direction side, Direction facing) {
        BlockState other = level.getBlockState(pos.relative(side));
        return other.getBlock() == this && other.getValue(FACING) == facing;
    }
}
