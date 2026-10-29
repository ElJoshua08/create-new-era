package com.createnewera.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A device on a computer's network, as its editor needs it for suggestions: what to call it,
 * what it is, where it is, how far away, and what a program can do with it through its ports.
 */
public record NetworkDevice(String name, String type, BlockPos pos, int distance, boolean readable, boolean writable,
                            List<String> methods) {
    private static final StreamCodec<ByteBuf, List<String>> METHODS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64));

    // by hand: StreamCodec.composite stops at six fields
    public static final StreamCodec<ByteBuf, NetworkDevice> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, d.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, d.type());
                BlockPos.STREAM_CODEC.encode(buf, d.pos());
                ByteBufCodecs.VAR_INT.encode(buf, d.distance());
                ByteBufCodecs.BOOL.encode(buf, d.readable());
                ByteBufCodecs.BOOL.encode(buf, d.writable());
                METHODS.encode(buf, d.methods());
            },
            buf -> new NetworkDevice(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), METHODS.decode(buf)));
}
