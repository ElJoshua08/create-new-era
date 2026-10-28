package com.createnewera.network;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.BrassComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: something the player did in the editor. Every action carries the current code. */
public record EditorActionPayload(BlockPos pos, Action action, String source) implements CustomPacketPayload {
    public enum Action { SAVE, RUN, STOP, CLOSE }

    public static final Type<EditorActionPayload> TYPE = new Type<>(CreateNewEra.asResource("editor_action"));

    public static final StreamCodec<FriendlyByteBuf, EditorActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EditorActionPayload::pos,
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), EditorActionPayload::action,
            ByteBufCodecs.stringUtf8(BrassComputerBlockEntity.MAX_SOURCE), EditorActionPayload::source,
            EditorActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
