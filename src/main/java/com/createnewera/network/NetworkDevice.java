package com.createnewera.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A device on a computer's network, as its editor needs it for suggestions: what to call it,
 * what it is, how far away, and what a program can do with it through its ports.
 */
public record NetworkDevice(String name, String type, int distance, boolean readable, boolean writable, List<String> methods) {
    public static final StreamCodec<ByteBuf, NetworkDevice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NetworkDevice::name,
            ByteBufCodecs.STRING_UTF8, NetworkDevice::type,
            ByteBufCodecs.VAR_INT, NetworkDevice::distance,
            ByteBufCodecs.BOOL, NetworkDevice::readable,
            ByteBufCodecs.BOOL, NetworkDevice::writable,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), NetworkDevice::methods,
            NetworkDevice::new);
}
