package com.createnewera.network;

import com.createnewera.CreateNewEra;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a speaker near the player said this. */
public record SpeakPayload(BlockPos pos, String text) implements CustomPacketPayload {
    public static final Type<SpeakPayload> TYPE = new Type<>(CreateNewEra.asResource("speak"));

    public static final StreamCodec<ByteBuf, SpeakPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SpeakPayload::pos,
            ByteBufCodecs.STRING_UTF8, SpeakPayload::text,
            SpeakPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
