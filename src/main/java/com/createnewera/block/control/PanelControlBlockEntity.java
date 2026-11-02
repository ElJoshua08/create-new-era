package com.createnewera.block.control;

import java.util.List;

import com.createnewera.bus.BusEvents;
import com.createnewera.bus.BusPort;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Any panel control. Its state lives in the block state; this is here for the client: what
 * engineer's (or aviator's) goggles show about it, the knob and slider drawn moving smoothly
 * while they're dragged, and lamps and nixies drawn glowing.
 */
public class PanelControlBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    /** Client only: where a knob or slider is drawn, 0 to 1, easing towards its value */
    public float shown = -1;
    /** Client only: the value the player is dragging it to, 0 to 1, or -1 when nobody is */
    public float dragged = -1;
    /** Client only: when it was last drawn, for easing by the time that passed */
    public long drawnAt;

    /** A knob or slider's range, set by programs: from min to max in steps of step */
    public double min = 0, max = 15, step = 1;
    /** Which notch of its range a knob or slider is at, 0 to {@link #notches()} */
    public int position = -1;

    public PanelControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How many steps from min to max: the last notch. A fine range is capped so it stays draggable. */
    public int notches() {
        return Math.clamp(Math.round((max - min) / step), 1, PanelDialBlock.MAX_NOTCHES);
    }

    /** The notch it's at; before any range was set, the one its block state shows. */
    public int position() {
        if (position < 0)
            return getBlockState().hasProperty(PanelDialBlock.VALUE) ? getBlockState().getValue(PanelDialBlock.VALUE) : 0;
        return Math.clamp(position, 0, notches());
    }

    /** The value at a notch: min, min + step... up to max, without float dust (0.1 * 3 is 0.3). */
    public double valueAt(int notch) {
        double v = notch >= notches() ? max : min + notch * (max - min) / notches();
        return Math.round(v * 1e6) / 1e6;
    }

    public double value() {
        return valueAt(position());
    }

    /** Where a knob or slider should be, 0 to 1: the drag in progress, or else its value. */
    public float target() {
        return dragged >= 0 ? dragged : (float) position() / notches();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (getBlockState().getBlock() instanceof PanelDialBlock) {
            tag.putDouble("min", min);
            tag.putDouble("max", max);
            tag.putDouble("step", step);
            tag.putInt("position", position());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("position")) {
            min = tag.getDouble("min");
            max = tag.getDouble("max");
            step = tag.getDouble("step");
            position = tag.getInt("position");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        // knobs and sliders show their value their own way while dragged, like the throttle lever
        if (!(state.getBlock() instanceof PanelControlBlock control) || control instanceof PanelDialBlock)
            return false;
        tooltip.add(Component.literal("    ").append(state.getBlock().getName()));
        tooltip.add(Component.literal("    ").append(control.goggleValue(state)).withStyle(ChatFormatting.GRAY));
        boolean cabled = level != null && BusEvents.clientParts.apply(worldPosition).stream()
                .anyMatch(p -> p.kind() == BusPort.Kind.CONTROL && BusEvents.clientCabled.test(p.id()));
        tooltip.add(Component.literal("    ").append(Component.translatable(cabled
                ? "create_new_era.goggles.cabled" : "create_new_era.goggles.not_cabled"))
                .withStyle(cabled ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));
        return true;
    }
}
