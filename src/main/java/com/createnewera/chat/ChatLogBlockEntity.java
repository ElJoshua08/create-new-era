package com.createnewera.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The messages a chat log has heard, newest last, up to {@link #KEPT}. It hears every chat
 * message in its dimension (ChatLogs passes them on) and counts them, so a program's onChange
 * fires on each one even when two say the same.
 */
public class ChatLogBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int KEPT = 50;

    /** One line of chat. @param time the game time it was said, in ticks */
    public record Message(String player, String text, long time) {
        public Map<String, Object> asTable() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("player", player);
            out.put("text", text);
            out.put("time", time);
            return out;
        }
    }

    private final Deque<Message> messages = new ArrayDeque<>();
    /** every message heard since it was placed */
    private long heard;

    public ChatLogBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void hear(String player, String text) {
        messages.addLast(new Message(player, text, level == null ? 0 : level.getGameTime()));
        while (messages.size() > KEPT)
            messages.removeFirst();
        heard++;
        setChanged();
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The last {@code count} messages, oldest first. */
    public List<Message> last(int count) {
        List<Message> all = new ArrayList<>(messages);
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    public long heard() {
        return heard;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server)
            ChatLogs.add(server, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server)
            ChatLogs.remove(server, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server)
            ChatLogs.remove(server, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, KEPT);
    }

    private void write(CompoundTag tag, int count) {
        ListTag list = new ListTag();
        for (Message m : last(count)) {
            CompoundTag line = new CompoundTag();
            line.putString("Player", m.player());
            line.putString("Text", m.text());
            line.putLong("Time", m.time());
            list.add(line);
        }
        tag.put("Messages", list);
        tag.putLong("Heard", heard);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        messages.clear();
        for (Tag t : tag.getList("Messages", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) t;
            messages.addLast(new Message(line.getString("Player"), line.getString("Text"), line.getLong("Time")));
        }
        heard = tag.getLong("Heard");
    }

    // clients get the last few, for goggles
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, 3);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tooltip.add(Component.literal("    ").append(Component.translatable("create_new_era.goggles.heard", heard)).withStyle(ChatFormatting.GRAY));
        for (Message m : last(3))
            tooltip.add(Component.literal("    <" + m.player() + "> " + m.text()).withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }
}
