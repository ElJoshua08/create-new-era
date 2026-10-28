package com.createnewera.network;

import com.createnewera.CreateNewEra;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player hit or used the cable under their crosshair. Cables aren't blocks,
 * so the client finds which one and the server checks it's within reach.
 */
public record CableActionPayload(long span, boolean attack) implements CustomPacketPayload {
    public static final Type<CableActionPayload> TYPE = new Type<>(CreateNewEra.asResource("cable_action"));

    public static final StreamCodec<ByteBuf, CableActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CableActionPayload::span,
            ByteBufCodecs.BOOL, CableActionPayload::attack,
            CableActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
