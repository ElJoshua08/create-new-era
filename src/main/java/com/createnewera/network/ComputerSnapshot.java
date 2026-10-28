package com.createnewera.network;

import java.util.List;

import com.createnewera.computer.ConsoleLine;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What an open editor shows besides the code: program state, console and redstone on each
 * side (indexed by {@link com.createnewera.computer.ComputerSide} ordinal).
 */
public record ComputerSnapshot(boolean running, List<ConsoleLine> console, List<Integer> inputs, List<Integer> outputs) {
    public static final int MAX_CONSOLE = 100;

    public static final StreamCodec<ByteBuf, ComputerSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ComputerSnapshot::running,
            ConsoleLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONSOLE)), ComputerSnapshot::console,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6)), ComputerSnapshot::inputs,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6)), ComputerSnapshot::outputs,
            ComputerSnapshot::new);
}
