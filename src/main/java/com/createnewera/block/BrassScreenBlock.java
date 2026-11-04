package com.createnewera.block;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.screen.BrassScreenBlockEntity;
import com.createnewera.block.screen.ScreenPane;
import com.createnewera.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
public class BrassScreenBlock extends HorizontalMachineBlock implements EntityBlock {
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrassScreenBlockEntity(ModBlockEntities.BRASS_SCREEN.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.BRASS_SCREEN.get())
            return null;
        return (l, p, s, be) -> BrassScreenBlockEntity.serverTick(l, p, s, (BrassScreenBlockEntity) be);
    }

    /** A click on the glass is a touch: where on the pane, for programs' onTouch. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING))
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        ScreenPane pane = ScreenPane.of(level, pos);
        if (pane == null || !(level.getBlockEntity(pane.origin()) instanceof BrassScreenBlockEntity controller))
            return InteractionResult.PASS;
        Direction right = ScreenPane.right(state.getValue(FACING));
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        double across = local.x * right.getStepX() + local.z * right.getStepZ();
        if (right.getStepX() + right.getStepZ() < 0)
            across += 1;
        int[] cell = pane.cell(pos);
        double u = cell[0] * 16 + across * 16 - ScreenPane.FRAME, v = cell[1] * 16 + (1 - local.y) * 16 - ScreenPane.FRAME;
        int x = (int) Math.floor(u * ScreenPane.DENSITY), y = (int) Math.floor(v * ScreenPane.DENSITY);
        if (x < 0 || y < 0 || x >= pane.width() || y >= pane.height())
            return InteractionResult.PASS; // the frame
        controller.touched(x, y, player.getGameProfile().getName());
        return InteractionResult.SUCCESS;
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
