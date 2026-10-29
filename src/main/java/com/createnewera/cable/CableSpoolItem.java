package com.createnewera.cable;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.control.PanelControlBlock;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusData;
import com.createnewera.bus.BusEvents;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.CableRoute;
import com.createnewera.bus.PartGeometry;
import com.createnewera.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A reel of data cable. Click a port or splice, then another: the cable runs between them,
 * hanging through the air, or (sneaking on the second click) flat along the faces in between.
 * Its durability is the cable left on it, in metres; an empty spool stays, to be wound again.
 */
public class CableSpoolItem extends Item {
    public static final int METRES = 64;

    public CableSpoolItem(Properties properties) {
        super(properties.durability(METRES).setNoRepair());
    }

    public static @Nullable DyeColor color(ItemStack stack) {
        return stack.get(ModComponents.CABLE_COLOR.get());
    }

    public static int metresLeft(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    // before the block gets the click, so a cable can start on a port stuck to a chest
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        List<BusPort> parts = level instanceof ServerLevel server
                ? BusData.get(server).portsAt(context.getClickedPos())
                : BusEvents.clientParts.apply(context.getClickedPos());
        BusPort part = PartGeometry.partAt(parts, context.getClickedFace(), context.getClickLocation());
        // a panel control takes the cable anywhere on it, into its own connection (fitted now
        // if it was placed before controls had one)
        if (part == null && level.getBlockState(context.getClickedPos()).getBlock() instanceof PanelControlBlock) {
            if (!(level instanceof ServerLevel server))
                return InteractionResult.SUCCESS;
            Bus.fitControl(server, context.getClickedPos(), level.getBlockState(context.getClickedPos()).getValue(PanelControlBlock.FACING));
            part = Bus.controlAt(BusData.get(server).portsAt(context.getClickedPos()));
        }
        if (part == null || !part.takesCables())
            return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server) || context.getPlayer() == null)
            return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        BusData data = BusData.get(server);
        DyeColor color = color(stack);
        CableStart start = stack.get(ModComponents.CABLE_START.get());
        BusPort from = start == null || start.dimension() != level.dimension() ? null : data.port(start.port());

        if (from == null) {
            if (!data.accepts(part.id(), color, -1))
                return refuse(player, Component.translatable("create_new_era.cable.color_clash"));
            if (Bus.full(data, part))
                return refuse(player, Component.translatable("create_new_era.cable.port_taken"));
            stack.set(ModComponents.CABLE_START.get(), new CableStart(part.id(), PartGeometry.anchor(part), level.dimension()));
            tell(player, Component.translatable("create_new_era.cable.started"));
            server.playSound(null, part.pos(), SoundEvents.COPPER_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
            return InteractionResult.SUCCESS;
        }
        if (from.id() == part.id()) {
            stack.remove(ModComponents.CABLE_START.get());
            tell(player, Component.translatable("create_new_era.cable.cancelled"));
            return InteractionResult.SUCCESS;
        }

        boolean aerial = !player.isShiftKeyDown();
        Bus.Plan plan = Bus.plan(server, from, part, color, aerial);
        if (plan.result() != Bus.Laid.OK)
            return refuse(player, Component.translatable("create_new_era.cable." + plan.result().name().toLowerCase()));
        int metres = CableRoute.metres(plan.points(), aerial);
        boolean creative = player.getAbilities().instabuild;
        if (!creative && metres > metresLeft(stack))
            return refuse(player, Component.translatable("create_new_era.cable.not_enough", metres, metresLeft(stack)));
        if (Bus.lay(server, from, part, color, aerial, plan.points()) == null)
            return refuse(player, Component.translatable("create_new_era.cable.color_clash"));
        if (!creative)
            stack.setDamageValue(stack.getDamageValue() + metres);
        stack.remove(ModComponents.CABLE_START.get());
        tell(player, Component.translatable("create_new_era.cable.laid", metres));
        server.playSound(null, part.pos(), SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 0.7f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    /** Sneak-click in the air lets go of the cable being run. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(ModComponents.CABLE_START.get()))
            return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.remove(ModComponents.CABLE_START.get());
            tell(player, Component.translatable("create_new_era.cable.cancelled"));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Puts cable that came off a span back on a spool of the same colour the player carries,
     * as far as it fits.
     */
    public static void refund(Player player, @Nullable DyeColor color, int metres) {
        if (player.getAbilities().instabuild)
            return;
        for (ItemStack stack : player.getInventory().items) {
            if (metres <= 0)
                return;
            if (!(stack.getItem() instanceof CableSpoolItem) || color(stack) != color)
                continue;
            int back = Math.min(metres, stack.getDamageValue());
            stack.setDamageValue(stack.getDamageValue() - back);
            metres -= back;
        }
    }

    private static InteractionResult refuse(Player player, Component why) {
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }

    private static void tell(Player player, Component what) {
        player.displayClientMessage(what, true);
    }

    @Override
    public Component getName(ItemStack stack) {
        DyeColor color = color(stack);
        return color == null ? super.getName(stack)
                : Component.translatable("item.create_new_era.cable_spool.dyed", Component.translatable("color.minecraft." + color.getName()));
    }

    // the bar is how much cable is left, so it's always worth showing
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_new_era.cable.metres_left", metresLeft(stack), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
    }
}
