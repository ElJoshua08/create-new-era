package com.createnewera.client.bus;

import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusEvents;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.PartGeometry;
import com.createnewera.bus.PortMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Draws the bus parts on top of whatever they're stuck to (they aren't blocks, so nothing else
 * would), and outlines the part you're looking at instead of the block under it.
 */
public final class BusPartRenderer {
    private static final ModelResourceLocation PORT_IN = model("bus_port_in");
    private static final ModelResourceLocation PORT_OUT = model("bus_port_out");
    private static final ModelResourceLocation EXPANSION = model("port_expansion");
    private static final ModelResourceLocation SPLICE = model("cable_splice");
    private static final ModelResourceLocation LAMP_IN = model("bus_port_lamp_in");
    private static final ModelResourceLocation LAMP_OUT = model("bus_port_lamp_out");
    /** Parts further than this aren't worth drawing: they're a few pixels across. */
    private static final double RANGE = 48;

    public static void register(IEventBus modBus, IEventBus gameBus) {
        BusEvents.clientParts = ClientBusParts::at;
        modBus.addListener(ModelEvent.RegisterAdditional.class, event -> {
            event.register(PORT_IN);
            event.register(PORT_OUT);
            event.register(EXPANSION);
            event.register(SPLICE);
            event.register(LAMP_IN);
            event.register(LAMP_OUT);
        });
        gameBus.addListener(BusPartRenderer::render);
        gameBus.addListener(BusPartRenderer::outline);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientBusParts.clear());
        gameBus.addListener(ChunkEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide())
                ClientBusParts.forget(event.getChunk().getPos().toLong());
        });
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(CreateNewEra.asResource("block/" + name));
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        var modelRenderer = mc.getBlockRenderer().getModelRenderer();
        var models = mc.getModelManager();

        for (List<BusPort> chunk : ClientBusParts.chunks()) {
            for (BusPort part : chunk) {
                if (part.pos().distToCenterSqr(camera) > RANGE * RANGE)
                    continue;
                // a computer's own ports are drawn by its side panels: only their lamp goes on top
                boolean builtIn = Bus.builtIn(mc.level.getBlockState(part.pos()), part);
                BakedModel model = models.getModel(switch (part.kind()) {
                    case PORT -> builtIn ? (part.mode() == PortMode.IN ? LAMP_IN : LAMP_OUT)
                            : part.mode() == PortMode.IN ? PORT_IN : PORT_OUT;
                    case EXPANSION -> EXPANSION;
                    case SPLICE -> SPLICE;
                });
                BlockPos outside = part.pos().relative(part.face());
                pose.pushPose();
                pose.translate(outside.getX() - camera.x, outside.getY() - camera.y, outside.getZ() - camera.z);
                // the same turn the game gives a block model pointing up, so drawing matches PartGeometry
                pose.translate(0.5, 0.5, 0.5);
                pose.mulPose(rotation(part.face()).getRotation().getLeftRotation());
                pose.translate(-0.5, -0.5, -0.5);
                pose.translate(part.u() / 16.0, 0, part.v() / 16.0);
                int light = LevelRenderer.getLightColor(mc.level, outside);
                modelRenderer.renderModel(pose.last(), consumer, null, model, 1, 1, 1, light, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, RenderType.cutout());
                pose.popPose();
            }
        }
        buffers.endBatch(RenderType.cutout());
    }

    /** The x/y rotation a blockstate gives a model pointing up to face {@code face}. */
    private static BlockModelRotation rotation(Direction face) {
        int x = face == Direction.DOWN ? 180 : face.getAxis().isHorizontal() ? 90 : 0;
        int y = face.getAxis().isHorizontal() ? ((int) face.toYRot() + 180) % 360 : 0;
        return BlockModelRotation.by(x, y);
    }

    private static void outline(RenderHighlightEvent.Block event) {
        BlockHitResult hit = event.getTarget();
        BusPort part = PartGeometry.partAt(ClientBusParts.at(hit.getBlockPos()), hit.getDirection(), hit.getLocation());
        if (part == null)
            return;
        event.setCanceled(true);
        Vec3 camera = event.getCamera().getPosition();
        AABB box = PartGeometry.box(part).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.lines()),
                box, 0, 0, 0, 0.4f);
    }

    private BusPartRenderer() {}
}
