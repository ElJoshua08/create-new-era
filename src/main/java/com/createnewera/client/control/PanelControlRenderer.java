package com.createnewera.client.control;

import com.createnewera.CreateNewEra;
import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.DirectionalShape;
import com.createnewera.block.control.NixieDigitBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeRenderer;
import com.createnewera.block.control.PanelControlBlockEntity;
import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelLampBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * What the block model can't do for a panel control: a knob's cap turned to its value (from
 * -135 to +135 degrees) and a slider's handle along its track, easing there so a drag looks
 * continuous; a lit lamp or nixie drawn at full brightness with a soft halo of its colour.
 */
public class PanelControlRenderer implements BlockEntityRenderer<PanelControlBlockEntity> {
    public static final ModelResourceLocation KNOB_CAP = model("panel_knob_cap");
    public static final ModelResourceLocation SLIDER_HANDLE = model("panel_slider_handle");
    /** How far the handle travels along the track, in blocks */
    private static final float TRAVEL = 8 / 16f;

    public PanelControlRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModelEvent.RegisterAdditional.class, event -> {
            event.register(KNOB_CAP);
            event.register(SLIDER_HANDLE);
            for (String light : new String[] {"amber", "green", "red"})
                event.register(model("panel_lamp_glow_" + light));
        });
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(CreateNewEra.asResource("block/" + name));
    }

    @Override
    public void render(PanelControlBlockEntity control, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = control.getBlockState();
        if (state.getBlock() instanceof NixieDigitBlock) {
            renderNixie(control, pose, buffers);
            return;
        }
        if (state.getBlock() instanceof com.createnewera.block.control.PanelInstrumentBlock instrument) {
            InstrumentRenderer.render(control, instrument, partialTick, pose, buffers, light);
            return;
        }
        pose.pushPose();
        // the turn the blockstate gives the model, so everything here lines up with it
        Direction facing = state.getValue(DirectionalPartBlock.FACING);
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(rotation(facing).getRotation().getLeftRotation());
        pose.translate(-0.5, -0.5, -0.5);
        if (state.getBlock() instanceof PanelDialBlock dial)
            renderDial(control, dial, pose, buffers, light, overlay);
        else if (state.getBlock() instanceof PanelLampBlock && state.getValue(PanelLampBlock.LIGHT) != PanelLampBlock.Light.OFF)
            renderLamp(state, pose, buffers, overlay);
        pose.popPose();
    }

    private static void renderDial(PanelControlBlockEntity control, PanelDialBlock dial, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
        // ease towards where it should be, by the time since the last frame
        long now = System.nanoTime();
        float target = control.target();
        if (control.shown < 0)
            control.shown = target;
        else
            control.shown += (target - control.shown) * Math.min(1, (now - control.drawnAt) / 1e9f * 18);
        control.drawnAt = now;
        boolean knob = dial.style() == PanelDialBlock.Style.KNOB;
        if (knob) {
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YN.rotationDegrees(-135 + 270 * control.shown));
            pose.translate(-0.5, 0, -0.5);
        } else {
            pose.translate(TRAVEL * control.shown, 0, 0);
        }
        draw(control.getBlockState(), knob ? KNOB_CAP : SLIDER_HANDLE, pose, buffers, light, overlay);
    }

    private static void renderLamp(BlockState state, PoseStack pose, MultiBufferSource buffers, int overlay) {
        PanelLampBlock.Light lit = state.getValue(PanelLampBlock.LIGHT);
        ShaderGlow.draw(() -> draw(state, model("panel_lamp_glow_" + lit.getSerializedName()), pose, buffers, LightTexture.FULL_BRIGHT, overlay));
        int rgb = switch (lit) {
            case AMBER -> 0xFFB03A;
            case GREEN -> 0x7FD06A;
            case RED -> 0xF06A4A;
            case OFF -> 0;
        };
        // the halo follows how bright it's set: a bigger, stronger glow at full brightness
        float glow = state.getValue(PanelLampBlock.BRIGHTNESS) / 15f;
        float grow = 1.5f * glow;
        halo(pose, buffers, 5.25f - grow, 2.75f - grow / 2, 5.25f - grow, 10.75f + grow, 6.75f + grow, 10.75f + grow, rgb, 0.15f + 0.35f * glow);
    }

    /**
     * A nixie's character, drawn by Create's own nixie tube renderer so it glows and flickers
     * like Create's: upright, facing the way the tube reads. On a wall the tube points out at
     * you and the character is seen end on, through the top of the glass, so it's drawn smaller.
     */
    private static void renderNixie(PanelControlBlockEntity tube, PoseStack pose, MultiBufferSource buffers) {
        if (tube.glyph.isBlank() || tube.getLevel() == null)
            return;
        BlockState state = tube.getBlockState();
        Direction facing = state.getValue(DirectionalPartBlock.FACING), reads = state.getValue(NixieDigitBlock.READS);
        boolean endOn = facing.getAxis().isHorizontal();
        // the middle of the glass (y 5-14 as authored, facing up); drawTube centres half a pixel low
        double[] at = DirectionalShape.map(8, facing == Direction.UP ? 10 : facing == Direction.DOWN ? 9 : 9.5, 8, facing);
        pose.pushPose();
        pose.translate(at[0] / 16, at[1] / 16, at[2] / 16);
        pose.mulPose(Axis.YP.rotationDegrees(-reads.toYRot()));
        float scale = endOn ? 0.035f : 0.05f;
        pose.scale(scale, -scale, scale);
        ShaderGlow.draw(() -> NixieTubeRenderer.drawTube(pose, buffers, tube.glyph, 3, tube.color, tube.getLevel().getRandom()));
        pose.popPose();
    }

    private static void draw(BlockState state, ModelResourceLocation where, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state,
                mc.getModelManager().getModel(where), 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    /**
     * A soft box of light round a lit part, blended additively so it brightens what's behind it,
     * in the model's pixels.
     */
    private static void halo(PoseStack pose, MultiBufferSource buffers, float x0, float y0, float z0, float x1, float y1, float z1,
                             int rgb, float alpha) {
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        var m = pose.last();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float a0 = x0 / 16, b0 = y0 / 16, c0 = z0 / 16, a1 = x1 / 16, b1 = y1 / 16, c1 = z1 / 16;
        float[][] quads = {
                {a0, b1, c0, a1, b1, c0, a1, b1, c1, a0, b1, c1}, // top
                {a0, b0, c0, a1, b0, c0, a1, b1, c0, a0, b1, c0}, // north
                {a0, b0, c1, a0, b1, c1, a1, b1, c1, a1, b0, c1}, // south
                {a0, b0, c0, a0, b1, c0, a0, b1, c1, a0, b0, c1}, // west
                {a1, b0, c0, a1, b0, c1, a1, b1, c1, a1, b1, c0}, // east
        };
        // both windings, so it shows from inside and out whatever culling the render type does
        for (float[] q : quads) {
            for (int i = 0; i < 12; i += 3)
                out.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, alpha);
            for (int i = 9; i >= 0; i -= 3)
                out.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, alpha);
        }
    }

    /** The x/y rotation a blockstate gives a model pointing up to face {@code face}. */
    static BlockModelRotation rotation(Direction face) {
        int x = face == Direction.DOWN ? 180 : face.getAxis().isHorizontal() ? 90 : 0;
        int y = face.getAxis().isHorizontal() ? ((int) face.toYRot() + 180) % 360 : 0;
        return BlockModelRotation.by(x, y);
    }
}
