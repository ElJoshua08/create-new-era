package com.createnewera.network;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.ComputerFiles;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: something the player did in the editor.
 *
 * @param name     the file it's about (unused by STOP and CLOSE)
 * @param argument the file's contents for SAVE, its new name for RENAME
 */
public record EditorActionPayload(BlockPos pos, Action action, String name, String argument) implements CustomPacketPayload {
    public enum Action { SAVE, DELETE, RENAME, RUN, STOP, CLOSE }

    public static final Type<EditorActionPayload> TYPE = new Type<>(CreateNewEra.asResource("editor_action"));

    public static final StreamCodec<FriendlyByteBuf, EditorActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EditorActionPayload::pos,
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), EditorActionPayload::action,
            ByteBufCodecs.stringUtf8(64), EditorActionPayload::name,
            ByteBufCodecs.stringUtf8(ComputerFiles.MAX_FILE), EditorActionPayload::argument,
            EditorActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
