package com.createnewera.network;

import com.createnewera.CreateNewEra;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player dragged a knob or slider to this value. */
public record DialPayload(BlockPos pos, int value) implements CustomPacketPayload {
    public static final Type<DialPayload> TYPE = new Type<>(CreateNewEra.asResource("dial"));

    public static final StreamCodec<ByteBuf, DialPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DialPayload::pos,
            ByteBufCodecs.VAR_INT, DialPayload::value,
            DialPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
