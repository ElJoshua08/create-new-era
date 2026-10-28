package com.createnewera.client.render;

import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

/**
 * The half shaft in the computer's back, lit from the block behind it: the computer is opaque,
 * so the light inside its own space is zero and the shaft would come out black.
 */
public class BrassComputerVisual extends OrientedRotatingVisual<BrassComputerBlockEntity> {
    private final Direction back;

    public BrassComputerVisual(VisualizationContext context, BrassComputerBlockEntity computer, float partialTick) {
        this(context, computer, partialTick, computer.getBlockState().getValue(BrassComputerBlock.HORIZONTAL_FACING).getOpposite());
    }

    private BrassComputerVisual(VisualizationContext context, BrassComputerBlockEntity computer, float partialTick, Direction back) {
        super(context, computer, partialTick, Direction.SOUTH, back, Models.partial(AllPartialModels.SHAFT_HALF));
        this.back = back;
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos.relative(back), rotatingModel);
    }
}
