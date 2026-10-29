package com.createnewera.cable;

import org.jetbrains.annotations.Nullable;

import com.createnewera.bus.Bus;
import com.createnewera.bus.BusData;
import com.createnewera.bus.BusSpan;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What a player can do to a laid cable: hit it to cut it (the cable goes back on a spool of its
 * colour if they carry one), dye it, case it in a Create casing, or take the casing off with a
 * wrench.
 */
public final class CableActions {
    /** Which items with the cable in sight mean something to it, so other clicks pass through. */
    public static boolean usesCable(ItemStack held) {
        return held.getItem() instanceof DyeItem || casingOf(held) != null || AllItems.WRENCH.isIn(held);
    }

    public static @Nullable BusSpan.Casing casingOf(ItemStack stack) {
        if (AllBlocks.ANDESITE_CASING.isIn(stack))
            return BusSpan.Casing.ANDESITE;
        if (AllBlocks.BRASS_CASING.isIn(stack))
            return BusSpan.Casing.BRASS;
        if (AllBlocks.COPPER_CASING.isIn(stack))
            return BusSpan.Casing.COPPER;
        return null;
    }

    private static ItemStack casingItem(BusSpan.Casing casing) {
        return switch (casing) {
            case ANDESITE -> AllBlocks.ANDESITE_CASING.asStack();
            case BRASS -> AllBlocks.BRASS_CASING.asStack();
            case COPPER -> AllBlocks.COPPER_CASING.asStack();
            case NONE -> ItemStack.EMPTY;
        };
    }

    public static void handle(ServerPlayer player, long spanId, boolean attack) {
        ServerLevel level = player.serverLevel();
        BusData data = BusData.get(level);
        BusSpan span = data.span(spanId);
        if (span == null || !inReach(player, span))
            return;
        boolean creative = player.getAbilities().instabuild;
        Vec3 at = span.points().get(span.points().size() / 2);

        if (attack) {
            if (Bus.cut(level, spanId) == null)
                return;
            CableSpoolItem.refund(player, span.color(), span.length());
            if (span.casing() != BusSpan.Casing.NONE && !creative)
                player.getInventory().placeItemBackInInventory(casingItem(span.casing()));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 0.7f, 1.3f);
            return;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof DyeItem dye) {
            if (!data.dye(spanId, dye.getDyeColor())) {
                if (span.color() != dye.getDyeColor())
                    player.displayClientMessage(Component.translatable("create_new_era.cable.color_clash").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!creative)
                held.shrink(1);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1, 1);
        } else if (casingOf(held) != null) {
            BusSpan.Casing casing = casingOf(held);
            if (!data.encase(spanId, casing))
                return;
            if (!creative) {
                held.shrink(1);
                if (span.casing() != BusSpan.Casing.NONE)
                    player.getInventory().placeItemBackInInventory(casingItem(span.casing()));
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 0.8f, 0.9f);
        } else if (AllItems.WRENCH.isIn(held)) {
            if (span.casing() == BusSpan.Casing.NONE || !data.encase(spanId, BusSpan.Casing.NONE))
                return;
            if (!creative)
                player.getInventory().placeItemBackInInventory(casingItem(span.casing()));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 0.6f, 1.1f);
        } else {
            return;
        }
        Bus.spanChanged(level, spanId);
    }

    /** The server's own check that the player can reach the cable they say they clicked. */
    private static boolean inReach(ServerPlayer player, BusSpan span) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() + 1.5;
        return CableShape.hit(span, player.level(), eye, player.getViewVector(1), reach) >= 0
                || span.points().stream().anyMatch(p -> p.distanceTo(eye) <= reach);
    }

    private CableActions() {}
}
