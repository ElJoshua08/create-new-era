package com.createnewera.computer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One line in the computer's console. */
public record ConsoleLine(Kind kind, String text) {
    public enum Kind {
        /** what the program printed */
        OUTPUT,
        /** errors, from the compiler or the running program */
        ERROR,
        /** the computer's own messages: started, stopped, finished. The text is a lang key. */
        SYSTEM
    }

    public static final int MAX_LENGTH = 256;

    public static final StreamCodec<ByteBuf, ConsoleLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), ConsoleLine::kind,
            ByteBufCodecs.stringUtf8(MAX_LENGTH), ConsoleLine::text,
            ConsoleLine::new);

    public ConsoleLine {
        if (text.length() > MAX_LENGTH)
            text = text.substring(0, MAX_LENGTH);
    }

    /** Stored as one string, the kind's initial first. */
    public String encode() {
        return kind.name().charAt(0) + text;
    }

    public static ConsoleLine decode(String stored) {
        if (stored.isEmpty())
            return new ConsoleLine(Kind.OUTPUT, "");
        Kind kind = switch (stored.charAt(0)) {
            case 'E' -> Kind.ERROR;
            case 'S' -> Kind.SYSTEM;
            default -> Kind.OUTPUT;
        };
        return new ConsoleLine(kind, stored.substring(1));
    }
}
