package com.createnewera.network;

import java.util.List;

import com.createnewera.computer.ConsoleLine;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What an open editor shows besides the code: program state, console, redstone on each
 * side (indexed by {@link com.createnewera.computer.ComputerSide} ordinal), how fast the
 * computer turns, which sets how fast the program runs, and the devices on its network,
 * nearest first.
 */
public record ComputerSnapshot(boolean running, List<ConsoleLine> console, List<Integer> inputs, List<Integer> outputs,
                               int rpm, boolean overstressed, List<NetworkDevice> devices) {
    public static final int MAX_CONSOLE = 100;
    public static final int MAX_DEVICES = 256;

    private static final StreamCodec<ByteBuf, List<ConsoleLine>> CONSOLE = ConsoleLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONSOLE));
    private static final StreamCodec<ByteBuf, List<Integer>> SIGNALS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6));
    private static final StreamCodec<ByteBuf, List<NetworkDevice>> DEVICES = NetworkDevice.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DEVICES));

    // by hand: StreamCodec.composite stops at six fields
    public static final StreamCodec<ByteBuf, ComputerSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                ByteBufCodecs.BOOL.encode(buf, s.running());
                CONSOLE.encode(buf, s.console());
                SIGNALS.encode(buf, s.inputs());
                SIGNALS.encode(buf, s.outputs());
                ByteBufCodecs.VAR_INT.encode(buf, s.rpm());
                ByteBufCodecs.BOOL.encode(buf, s.overstressed());
                DEVICES.encode(buf, s.devices());
            },
            buf -> new ComputerSnapshot(ByteBufCodecs.BOOL.decode(buf), CONSOLE.decode(buf), SIGNALS.decode(buf),
                    SIGNALS.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), DEVICES.decode(buf)));
}
