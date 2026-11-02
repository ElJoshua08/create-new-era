package com.createnewera.network;

import com.createnewera.CreateNewEra;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a key the player pressed or released on a linked typewriter that has no
 * frequency bound to it. Aeronautics only sends bound keys; programs on the bus want them all.
 */
public record TypewriterKeyPayload(BlockPos pos, int key, boolean down) implements CustomPacketPayload {
    public static final Type<TypewriterKeyPayload> TYPE = new Type<>(CreateNewEra.asResource("typewriter_key"));

    public static final StreamCodec<ByteBuf, TypewriterKeyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TypewriterKeyPayload::pos,
            ByteBufCodecs.VAR_INT, TypewriterKeyPayload::key,
            ByteBufCodecs.BOOL, TypewriterKeyPayload::down,
            TypewriterKeyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
