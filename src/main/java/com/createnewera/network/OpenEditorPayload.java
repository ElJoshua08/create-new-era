package com.createnewera.network;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.BrassComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the editor for the computer at pos. */
public record OpenEditorPayload(BlockPos pos, String source, ComputerSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<OpenEditorPayload> TYPE = new Type<>(CreateNewEra.asResource("open_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenEditorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenEditorPayload::pos,
            ByteBufCodecs.stringUtf8(BrassComputerBlockEntity.MAX_SOURCE), OpenEditorPayload::source,
            ComputerSnapshot.STREAM_CODEC, OpenEditorPayload::snapshot,
            OpenEditorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
