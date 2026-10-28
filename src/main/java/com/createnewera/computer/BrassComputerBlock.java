package com.createnewera.computer;

import com.createnewera.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

/**
 * Runs on rotational force like any other Create machine: a shaft goes into its back, and the
 * faster it spins, the more of each tick its program gets.
 */
public class BrassComputerBlock extends HorizontalKineticBlock implements IBE<BrassComputerBlockEntity> {
    /** Lights the screen up; mirrors whether a program is loaded. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** Stress per RPM, as Create counts it. */
    public static final double STRESS_IMPACT = 2.0;

    public BrassComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // like Create's cuckoo clock: back towards a neighbouring shaft if there is one, else face the player
        Direction preferred = getPreferredHorizontalFacing(context);
        Direction facing = preferred != null ? preferred.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public Class<BrassComputerBlockEntity> getBlockEntityClass() {
        return BrassComputerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrassComputerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BRASS_COMPUTER.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // let the wrench rotate us instead of opening the editor
        if (AllItems.WRENCH.isIn(stack) || stack.is(Tags.Items.TOOLS_WRENCH))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer)
            withBlockEntityDo(level, pos, computer -> computer.openEditor(serverPlayer));
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // direction points from the neighbour back at us, so the signal leaves through its opposite
        return level.getBlockEntity(pos) instanceof BrassComputerBlockEntity computer
                ? computer.getOutput(direction.getOpposite())
                : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
