package com.createnewera.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A device on a computer's network, as its editor needs it for suggestions: what to call it,
 * what it is (its id, and the lang key of its name), where it is, how far away, and what a program can do with it through its ports.
 */
public record NetworkDevice(String name, String type, String typeKey, BlockPos pos, int distance, boolean readable,
                            boolean writable, List<String> reads, List<String> orders) {
    private static final StreamCodec<ByteBuf, List<String>> METHODS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64));

    /** Everything a program can call on it through its ports. */
    public List<String> methods() {
        List<String> all = new ArrayList<>(reads);
        all.addAll(orders);
        return all;
    }

    // by hand: StreamCodec.composite stops at six fields
    public static final StreamCodec<ByteBuf, NetworkDevice> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, d.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, d.type());
                ByteBufCodecs.STRING_UTF8.encode(buf, d.typeKey());
                BlockPos.STREAM_CODEC.encode(buf, d.pos());
                ByteBufCodecs.VAR_INT.encode(buf, d.distance());
                ByteBufCodecs.BOOL.encode(buf, d.readable());
                ByteBufCodecs.BOOL.encode(buf, d.writable());
                METHODS.encode(buf, d.reads());
                METHODS.encode(buf, d.orders());
            },
            buf -> new NetworkDevice(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), METHODS.decode(buf), METHODS.decode(buf)));
}
