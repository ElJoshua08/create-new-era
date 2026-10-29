package com.createnewera.network;

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
                            boolean writable, List<Fn> functions) {
    /**
     * One function as the editor shows it.
     *
     * @param doc   lang key of what it does
     * @param reads reads the device rather than giving it an order
     */
    public record Fn(String name, String params, String returns, String doc, boolean reads) {
        public String signature() {
            return name + "(" + params + ")" + (returns.isEmpty() ? "" : " → " + returns);
        }

        private static final StreamCodec<ByteBuf, Fn> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Fn::name,
                ByteBufCodecs.STRING_UTF8, Fn::params,
                ByteBufCodecs.STRING_UTF8, Fn::returns,
                ByteBufCodecs.STRING_UTF8, Fn::doc,
                ByteBufCodecs.BOOL, Fn::reads,
                Fn::new);
    }

    private static final StreamCodec<ByteBuf, List<Fn>> FUNCTIONS = Fn.CODEC.apply(ByteBufCodecs.list(64));

    /** Every function's name. */
    public List<String> methods() {
        return functions.stream().map(Fn::name).toList();
    }

    public List<Fn> reads() {
        return functions.stream().filter(f -> f.reads() && !f.name().equals("read")).toList();
    }

    public List<Fn> orders() {
        return functions.stream().filter(f -> !f.reads()).toList();
    }

    /** read(): the device's whole state in one value, also what onChange hands its function. */
    public java.util.Optional<Fn> read() {
        return functions.stream().filter(f -> f.name().equals("read")).findFirst();
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
                FUNCTIONS.encode(buf, d.functions());
            },
            buf -> new NetworkDevice(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), FUNCTIONS.decode(buf)));
}
