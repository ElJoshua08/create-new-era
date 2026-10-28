package com.createnewera.client.render;

import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The half shaft turning in the computer's back, for when Flywheel isn't drawing it (shaders
 * often turn Flywheel off). Lit from the block behind: inside the opaque computer it's dark.
 */
public class BrassComputerRenderer extends KineticBlockEntityRenderer<BrassComputerBlockEntity> {
    public BrassComputerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BrassComputerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        Direction back = be.getBlockState().getValue(BrassComputerBlock.HORIZONTAL_FACING).getOpposite();
        int behind = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(back));
        super.renderSafe(be, partialTicks, ms, buffer, behind, overlay);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(BrassComputerBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                state.getValue(BrassComputerBlock.HORIZONTAL_FACING).getOpposite());
    }
}
