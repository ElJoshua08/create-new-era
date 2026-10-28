package com.createnewera.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A block that faces any of the six directions: panel controls, adapters, servos. */
public class DirectionalPartBlock extends DirectionalBlock implements IWrenchable {
    public enum Placement {
        /** Points away from the face it was placed against, like a button. */
        AGAINST_FACE,
        /** Points back at the player, like Create's bearings. */
        TOWARDS_PLAYER
    }

    private final DirectionalShape shape;
    private final Placement placement;

    public DirectionalPartBlock(Properties properties, DirectionalShape shape, Placement placement) {
        super(properties);
        this.shape = shape;
        this.placement = placement;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(p -> new DirectionalPartBlock(p, shape, placement));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, switch (placement) {
            case AGAINST_FACE -> context.getClickedFace();
            case TOWARDS_PLAYER -> context.getNearestLookingDirection().getOpposite();
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape == null ? Shapes.block() : shape.get(state.getValue(FACING));
    }
}
