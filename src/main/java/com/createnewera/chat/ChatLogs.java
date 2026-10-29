package com.createnewera.chat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Where the loaded chat logs are, per dimension, so each chat message reaches the ones that hear it. */
public final class ChatLogs {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ChatLogs::heard);
    }

    static void add(ServerLevel level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> here = LOADED.get(level.dimension());
        if (here != null)
            here.remove(pos);
    }

    /** Every chat log in the dimension a player spoke in hears it. */
    private static void heard(ServerChatEvent event) {
        ServerLevel level = event.getPlayer().serverLevel();
        for (BlockPos pos : Set.copyOf(LOADED.getOrDefault(level.dimension(), Set.of())))
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ChatLogBlockEntity log)
                log.hear(event.getUsername(), event.getRawText());
    }

    private ChatLogs() {}
}
