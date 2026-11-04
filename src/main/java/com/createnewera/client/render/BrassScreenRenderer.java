package com.createnewera.client.render;

import java.util.HashMap;
import java.util.Map;

import com.createnewera.CreateNewEra;
import com.createnewera.block.HorizontalMachineBlock;
import com.createnewera.block.screen.BrassScreenBlockEntity;
import com.createnewera.block.screen.ScreenCanvas;
import com.createnewera.block.screen.ScreenPane;
import com.createnewera.client.control.ShaderGlow;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * What a pane of Brass Screens shows: each screen draws its own share of the pane's picture on
 * its glass, at full brightness, as a screen gives its own light. The picture is a texture per
 * pane, uploaded again only when new pixels arrive.
 */
public class BrassScreenRenderer implements BlockEntityRenderer<BrassScreenBlockEntity> {
    /** Just in front of the glass, which sits 1.5 pixels into the block from its front */
    private static final float DEPTH = 1.5f / 16 - 0.002f;

    public BrassScreenRenderer(BlockEntityRendererProvider.Context context) {
        BrassScreenBlockEntity.clientRemoved = Textures::release;
    }

    @Override
    public void render(BrassScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = screen.getLevel();
        if (level == null)
            return;
        ScreenPane pane = ScreenPane.of(level, screen.getBlockPos());
        if (pane == null || !(level.getBlockEntity(pane.origin()) instanceof BrassScreenBlockEntity controller))
            return;
        ScreenCanvas canvas = controller.shown();
        if (canvas == null || canvas.width() != pane.width() || canvas.height() != pane.height())
            return;
        ResourceLocation texture = Textures.of(controller, canvas);

        // this block's share of the glass, in the pane's own pixels (16 a block), left to right and top down
        int[] cell = pane.cell(screen.getBlockPos());
        int paneW = pane.columns() * 16, paneH = pane.rows() * 16, frame = ScreenPane.FRAME;
        float u0 = Math.max(cell[0] * 16, frame), u1 = Math.min(cell[0] * 16 + 16, paneW - frame);
        float v0 = Math.max(cell[1] * 16, frame), v1 = Math.min(cell[1] * 16 + 16, paneH - frame);
        if (u1 <= u0 || v1 <= v0)
            return;

        Direction facing = screen.getBlockState().getValue(HorizontalMachineBlock.FACING);
        pose.pushPose();
        // the turn the blockstate gives the model, authored facing north with +x on the viewer's left
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(BlockModelRotation.by(0, ((int) facing.toYRot() + 180) % 360).getRotation().getLeftRotation());
        pose.translate(-0.5, -0.5, -0.5);
        float x0 = (16 - (u0 - cell[0] * 16)) / 16, x1 = (16 - (u1 - cell[0] * 16)) / 16;
        float y0 = (16 - (v0 - cell[1] * 16)) / 16, y1 = (16 - (v1 - cell[1] * 16)) / 16;
        float s0 = (u0 - frame) / (paneW - 2 * frame), s1 = (u1 - frame) / (paneW - 2 * frame);
        float t0 = (v0 - frame) / (paneH - 2 * frame), t1 = (v1 - frame) / (paneH - 2 * frame);
        var last = pose.last();
        ShaderGlow.draw(() -> {
            VertexConsumer out = buffers.getBuffer(RenderType.text(texture));
            // both windings: it shows from the front whatever the render type culls
            vertex(out, last, x0, y0, s0, t0);
            vertex(out, last, x0, y1, s0, t1);
            vertex(out, last, x1, y1, s1, t1);
            vertex(out, last, x1, y0, s1, t0);
            vertex(out, last, x1, y0, s1, t0);
            vertex(out, last, x1, y1, s1, t1);
            vertex(out, last, x0, y1, s0, t1);
            vertex(out, last, x0, y0, s0, t0);
        });
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, DEPTH).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    @Override
    public boolean shouldRenderOffScreen(BrassScreenBlockEntity screen) {
        return true;
    }

    /** One texture per pane, by its top-left screen, uploaded when its pixels change. */
    public static final class Textures {
        private record Entry(DynamicTexture texture, ResourceLocation id, int version, int width, int height) {}

        private static final Map<BlockPos, Entry> BY_PANE = new HashMap<>();

        static ResourceLocation of(BrassScreenBlockEntity controller, ScreenCanvas canvas) {
            BlockPos at = controller.getBlockPos();
            Entry entry = BY_PANE.get(at);
            if (entry != null && entry.version == controller.version && entry.width == canvas.width() && entry.height == canvas.height())
                return entry.id;
            var textures = Minecraft.getInstance().getTextureManager();
            if (entry == null || entry.width != canvas.width() || entry.height != canvas.height()) {
                if (entry != null)
                    textures.release(entry.id);
                DynamicTexture texture = new DynamicTexture(canvas.width(), canvas.height(), false);
                ResourceLocation id = CreateNewEra.asResource("screen/" + at.getX() + "_" + at.getY() + "_" + at.getZ() + "_" + System.nanoTime());
                textures.register(id, texture);
                entry = new Entry(texture, id, -1, canvas.width(), canvas.height());
            }
            NativeImage image = entry.texture.getPixels();
            int[] px = canvas.pixels();
            for (int y = 0; y < canvas.height(); y++)
                for (int x = 0; x < canvas.width(); x++) {
                    int rgb = px[y * canvas.width() + x];
                    // NativeImage keeps ABGR
                    image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
                }
            entry.texture.upload();
            entry = new Entry(entry.texture, entry.id, controller.version, entry.width, entry.height);
            BY_PANE.put(at, entry);
            return entry.id;
        }

        /** The pane's top-left screen went away: its texture goes too. */
        public static void release(BlockPos controller) {
            Entry entry = BY_PANE.remove(controller);
            if (entry != null)
                Minecraft.getInstance().getTextureManager().release(entry.id);
        }
    }
}
