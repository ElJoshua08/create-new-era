package com.createnewera.client.control;

import com.createnewera.CreateNewEra;
import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelDialBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The part of a knob or slider that moves: the knob's cap turned to its value (from -135 to
 * +135 degrees), the slider's handle along its track, easing there so a drag looks continuous.
 */
public class PanelDialRenderer implements BlockEntityRenderer<PanelDialBlockEntity> {
    public static final ModelResourceLocation KNOB_CAP = model("panel_knob_cap");
    public static final ModelResourceLocation SLIDER_HANDLE = model("panel_slider_handle");
    /** How far the handle travels along the track, in blocks */
    private static final float TRAVEL = 8 / 16f;

    public PanelDialRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModelEvent.RegisterAdditional.class, event -> {
            event.register(KNOB_CAP);
            event.register(SLIDER_HANDLE);
        });
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(CreateNewEra.asResource("block/" + name));
    }

    @Override
    public void render(PanelDialBlockEntity dial, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(dial.getBlockState().getBlock() instanceof PanelDialBlock block))
            return;
        // ease towards where it should be, by the time since the last frame
        long now = System.nanoTime();
        float target = dial.target();
        if (dial.shown < 0)
            dial.shown = target;
        else
            dial.shown += (target - dial.shown) * Math.min(1, (now - dial.drawnAt) / 1e9f * 18);
        dial.drawnAt = now;

        Direction facing = dial.getBlockState().getValue(DirectionalPartBlock.FACING);
        pose.pushPose();
        // the turn the blockstate gives the model, so this lines up with the base
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(rotation(facing).getRotation().getLeftRotation());
        pose.translate(-0.5, -0.5, -0.5);
        boolean knob = block.style() == PanelDialBlock.Style.KNOB;
        if (knob) {
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YN.rotationDegrees(-135 + 270 * dial.shown));
            pose.translate(-0.5, 0, -0.5);
        } else {
            pose.translate(TRAVEL * dial.shown, 0, 0);
        }
        var mc = Minecraft.getInstance();
        var model = mc.getModelManager().getModel(knob ? KNOB_CAP : SLIDER_HANDLE);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                dial.getBlockState(), model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    /** The x/y rotation a blockstate gives a model pointing up to face {@code face}. */
    static BlockModelRotation rotation(Direction face) {
        int x = face == Direction.DOWN ? 180 : face.getAxis().isHorizontal() ? 90 : 0;
        int y = face.getAxis().isHorizontal() ? ((int) face.toYRot() + 180) % 360 : 0;
        return BlockModelRotation.by(x, y);
    }
}
