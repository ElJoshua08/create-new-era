package com.createnewera.client.render;

import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** The half shaft turning in the computer's back, for when Flywheel isn't drawing it. */
public class BrassComputerRenderer extends KineticBlockEntityRenderer<BrassComputerBlockEntity> {
    public BrassComputerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(BrassComputerBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                state.getValue(BrassComputerBlock.HORIZONTAL_FACING).getOpposite());
    }
}
