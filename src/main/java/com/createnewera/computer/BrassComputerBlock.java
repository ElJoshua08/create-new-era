package com.createnewera.computer;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.HorizontalMachineBlock;
import com.createnewera.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

public class BrassComputerBlock extends HorizontalMachineBlock implements EntityBlock {
    public static final MapCodec<BrassComputerBlock> CODEC = simpleCodec(BrassComputerBlock::new);
    /** Lights the screen up; mirrors whether a program is loaded. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public BrassComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RUNNING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.BRASS_COMPUTER.create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.BRASS_COMPUTER.get())
            return null;
        return (l, pos, s, be) -> BrassComputerBlockEntity.serverTick(l, pos, s, (BrassComputerBlockEntity) be);
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
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BrassComputerBlockEntity computer)
            computer.openEditor(serverPlayer);
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
