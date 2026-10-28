package com.createnewera.network;

import com.createnewera.CreateNewEra;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: live updates for an open editor. */
public record ComputerStatePayload(BlockPos pos, ComputerSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ComputerStatePayload> TYPE = new Type<>(CreateNewEra.asResource("computer_state"));

    public static final StreamCodec<FriendlyByteBuf, ComputerStatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ComputerStatePayload::pos,
            ComputerSnapshot.STREAM_CODEC, ComputerStatePayload::snapshot,
            ComputerStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
