package com.createnewera.client.bus;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusEvents;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.BusSpan;
import com.createnewera.bus.CableRoute;
import com.createnewera.bus.PartGeometry;
import com.createnewera.cable.CableActions;
import com.createnewera.cable.CableShape;
import com.createnewera.cable.CableSpoolItem;
import com.createnewera.cable.CableStart;
import com.createnewera.network.CableActionPayload;
import com.createnewera.registry.ModComponents;
import com.createnewera.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Draws laid cables as braided tubes (thicker, in casing, when encased), the cable following
 * the player between a spool's two clicks, and turns clicks on a cable into actions on it.
 */
public final class CableRenderer {
    private static final double RANGE = 64;

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(CableRenderer::spoolModels));
        gameBus.addListener(CableRenderer::render);
        gameBus.addListener(CableRenderer::click);
    }

    /** The spool's model follows its colour and how much cable is left on it. */
    private static void spoolModels() {
        ItemProperties.register(ModItems.CABLE_SPOOL.get(), CreateNewEra.asResource("dye"), (stack, level, entity, seed) -> {
            DyeColor color = CableSpoolItem.color(stack);
            return color == null ? 0 : color.getId() + 1;
        });
        ItemProperties.register(ModItems.CABLE_SPOOL.get(), CreateNewEra.asResource("fill"), (stack, level, entity, seed) -> {
            int left = CableSpoolItem.metresLeft(stack);
            return left <= 0 ? 0 : left * 2 < stack.getMaxDamage() ? 1 : 2;
        });
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    private static ResourceLocation texture(BusSpan span) {
        return switch (span.casing()) {
            case ANDESITE -> ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing");
            case BRASS -> ResourceLocation.fromNamespaceAndPath("create", "block/brass_casing");
            case COPPER -> ResourceLocation.fromNamespaceAndPath("create", "block/copper_casing");
            case NONE -> cableTexture(span.color());
        };
    }

    private static ResourceLocation cableTexture(@Nullable DyeColor color) {
        return CreateNewEra.asResource("block/cable/" + (color == null ? "copper" : color.getName()));
    }

    // ---- drawing ----

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        for (BusSpan span : ClientBusParts.spans()) {
            if (span.points().get(0).distanceToSqr(camera) > RANGE * RANGE)
                continue;
            tube(pose, consumer, mc.level, CableShape.line(span, mc.level), CableShape.radius(span), sprite(texture(span)), 0xFFFFFF);
        }
        preview(mc, pose, consumer);
        buffers.endBatch(RenderType.cutout());

        BusSpan hovered = hovered(mc, mc.player.blockInteractionRange());
        if (hovered != null) {
            outline(pose, buffers.getBuffer(RenderType.lines()), CableShape.line(hovered, mc.level), CableShape.radius(hovered) + 0.01);
            buffers.endBatch(RenderType.lines());
        }
        pose.popPose();
    }

    /** Between a spool's two clicks: the cable as it would go, red where it can't. */
    private static void preview(Minecraft mc, PoseStack pose, VertexConsumer consumer) {
        LocalPlayer player = mc.player;
        ItemStack held = player.getMainHandItem();
        CableStart start = held.get(ModComponents.CABLE_START.get());
        if (start == null || start.dimension() != mc.level.dimension())
            return;
        DyeColor color = CableSpoolItem.color(held);
        boolean aerial = !player.isShiftKeyDown();
        BusPort from = ClientBusParts.part(start.port());
        BusPort to = target(mc);

        List<Vec3> line = null;
        boolean ok = false;
        if (to != null && from != null && to.id() != from.id()) {
            List<Vec3> route = aerial ? CableRoute.aerial(from, to) : CableRoute.surface(mc.level, from, to);
            boolean colours = BusSpan.compatible(ClientBusParts.colorAt(to.id()), color);
            if (route != null) {
                line = aerial ? CableShape.hanging(route.get(0), route.get(1), mc.level, CableShape.BARE) : route;
                ok = colours && (player.getAbilities().instabuild
                        || CableRoute.metres(route, aerial) <= CableSpoolItem.metresLeft(held));
            }
        }
        if (line == null) {
            HitResult hit = mc.hitResult;
            Vec3 end = to != null ? PartGeometry.anchor(to, start.anchor())
                    : hit != null && hit.getType() != HitResult.Type.MISS ? hit.getLocation()
                    : player.getEyePosition().add(player.getViewVector(1).scale(4));
            line = CableShape.hanging(start.anchor(), end, mc.level, CableShape.BARE);
            ok = to == null && start.anchor().distanceTo(end) <= CableRoute.MAX_AERIAL;
        }
        tube(pose, consumer, mc.level, line, CableShape.BARE * 0.75, sprite(cableTexture(color)), ok ? 0xFFFFFF : 0xFF5050);
    }

    /** The cable-taking part under the crosshair, if any. */
    private static @Nullable BusPort target(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS)
            return null;
        BusPort part = PartGeometry.partAt(ClientBusParts.at(hit.getBlockPos()), hit.getDirection(), hit.getLocation());
        return part != null && part.takesCables() ? part : null;
    }

    /**
     * A square tube along the line. Each piece runs a little past its ends, so the bends of a
     * surface cable close up without mitring.
     */
    private static void tube(PoseStack pose, VertexConsumer out, Level level, List<Vec3> line, double r,
                             TextureAtlasSprite sprite, int tint) {
        PoseStack.Pose last = pose.last();
        int tr = tint >> 16 & 255, tg = tint >> 8 & 255, tb = tint & 255;
        for (int i = 1; i < line.size(); i++) {
            Vec3 a = line.get(i - 1), b = line.get(i);
            double length = a.distanceTo(b);
            if (length < 1e-5)
                continue;
            Vec3 t = b.subtract(a).scale(1 / length);
            a = a.subtract(t.scale(r));
            b = b.add(t.scale(r));
            Vec3 side = t.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-6)
                side = t.cross(new Vec3(1, 0, 0));
            side = side.normalize().scale(r);
            Vec3 up = side.cross(t).normalize().scale(r);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(a.lerp(b, 0.5)));
            float u0 = sprite.getU(0), u1 = sprite.getU((float) Math.min(1, length + 2 * r));
            float v0 = sprite.getV(0), v1 = sprite.getV((float) Math.min(1, 2 * r));
            Vec3[] around = {up, side, up.scale(-1), side.scale(-1)};
            for (int f = 0; f < 4; f++) {
                Vec3 n = around[f], m = around[(f + 1) % 4];
                // corners of this face: (n + m) and (n - m), at both ends
                Vec3 p0 = a.add(n).subtract(m), p1 = a.add(n).add(m), p2 = b.add(n).add(m), p3 = b.add(n).subtract(m);
                Vec3 normal = n.normalize();
                float shade = shade(normal);
                int cr = (int) (tr * shade), cg = (int) (tg * shade), cb = (int) (tb * shade);
                vertex(out, last, p0, cr, cg, cb, u0, v0, light, normal);
                vertex(out, last, p1, cr, cg, cb, u0, v1, light, normal);
                vertex(out, last, p2, cr, cg, cb, u1, v1, light, normal);
                vertex(out, last, p3, cr, cg, cb, u1, v0, light, normal);
            }
        }
    }

    /** The same face shading blocks get: full on top, darker on the sides, darkest below. */
    private static float shade(Vec3 n) {
        double y = n.y > 0 ? n.y * n.y : n.y * n.y * 0.5;
        return (float) Math.min(1, y + n.x * n.x * 0.6 + n.z * n.z * 0.8);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, int r, int g, int b, float u, float v,
                               int light, Vec3 n) {
        out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255).setUv(u, v).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    /** The tube's four edges, like a block's outline. */
    private static void outline(PoseStack pose, VertexConsumer out, List<Vec3> line, double r) {
        PoseStack.Pose last = pose.last();
        for (int i = 1; i < line.size(); i++) {
            Vec3 a = line.get(i - 1), b = line.get(i);
            Vec3 t = b.subtract(a);
            if (t.lengthSqr() < 1e-10)
                continue;
            t = t.normalize();
            Vec3 side = t.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-6)
                side = t.cross(new Vec3(1, 0, 0));
            side = side.normalize().scale(r);
            Vec3 up = side.cross(t).normalize().scale(r);
            for (Vec3 corner : new Vec3[] {up.add(side), up.subtract(side), side.subtract(up), up.add(side).scale(-1)}) {
                Vec3 p = a.add(corner), q = b.add(corner);
                out.addVertex(last, (float) p.x, (float) p.y, (float) p.z).setColor(0, 0, 0, 102).setNormal(last, (float) t.x, (float) t.y, (float) t.z);
                out.addVertex(last, (float) q.x, (float) q.y, (float) q.z).setColor(0, 0, 0, 102).setNormal(last, (float) t.x, (float) t.y, (float) t.z);
            }
        }
    }

    // ---- clicking ----

    /** The cable under the crosshair, if it's nearer than the block there. */
    private static @Nullable BusSpan hovered(Minecraft mc, double reach) {
        Vec3 eye = mc.player.getEyePosition();
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS)
            reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(eye));
        return CableShape.pick(ClientBusParts.spans(), mc.level, eye, mc.player.getViewVector(1), reach);
    }

    private static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isPickBlock() && mc.player != null) {
            ItemStack picked = picked(mc);
            if (picked != null && !picked.isEmpty()) {
                pick(mc, picked);
                event.setCanceled(true);
            }
            return;
        }
        if (mc.player == null || event.getHand() != InteractionHand.MAIN_HAND || !(event.isAttack() || event.isUseItem()))
            return;
        if (event.isUseItem() && !CableActions.usesCable(mc.player.getMainHandItem()))
            return;
        BusSpan span = hovered(mc, mc.player.blockInteractionRange());
        if (span == null)
            return;
        PacketDistributor.sendToServer(new CableActionPayload(span.id(), event.isAttack()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    /**
     * What middle click should pick: the cable under the crosshair (a spool of its colour), or
     * the part on the block face there; null to leave it to the game.
     */
    private static @Nullable ItemStack picked(Minecraft mc) {
        BusSpan span = hovered(mc, mc.player.blockInteractionRange());
        if (span != null) {
            ItemStack spool = ModItems.CABLE_SPOOL.asStack();
            if (span.color() != null)
                spool.set(ModComponents.CABLE_COLOR.get(), span.color());
            return spool;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS)
            return null;
        BusPort part = PartGeometry.partAt(ClientBusParts.at(hit.getBlockPos()), hit.getDirection(), hit.getLocation());
        if (part == null || Bus.builtIn(mc.level.getBlockState(part.pos()), part))
            return null;
        return BusEvents.item(part);
    }

    /** Picks an item as the game's own middle click does: conjured in creative, found in the inventory otherwise. */
    private static void pick(Minecraft mc, ItemStack stack) {
        var player = mc.player;
        var inventory = player.getInventory();
        if (player.getAbilities().instabuild) {
            inventory.setPickedItem(stack);
            mc.gameMode.handleCreativeModeItemAdd(player.getItemInHand(InteractionHand.MAIN_HAND), 36 + inventory.selected);
            return;
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack held = inventory.items.get(slot);
            boolean same = held.is(stack.getItem()) && (!stack.is(ModItems.CABLE_SPOOL.get())
                    || CableSpoolItem.color(held) == CableSpoolItem.color(stack));
            if (!same)
                continue;
            if (net.minecraft.world.entity.player.Inventory.isHotbarSlot(slot))
                inventory.selected = slot;
            else
                mc.gameMode.handlePickItem(slot);
            return;
        }
    }

    private CableRenderer() {}
}
