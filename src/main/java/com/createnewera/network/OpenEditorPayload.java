package com.createnewera.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.ComputerFiles;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the editor for the computer at pos, with its whole disk. */
public record OpenEditorPayload(BlockPos pos, List<String> order, Map<String, String> files, String entry,
                                ComputerSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<OpenEditorPayload> TYPE = new Type<>(CreateNewEra.asResource("open_editor"));

    private static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(64);

    public static final StreamCodec<FriendlyByteBuf, OpenEditorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenEditorPayload::pos,
            NAME.apply(ByteBufCodecs.list(ComputerFiles.MAX_FILES)), OpenEditorPayload::order,
            ByteBufCodecs.map(HashMap::new, NAME, ByteBufCodecs.stringUtf8(ComputerFiles.MAX_FILE), ComputerFiles.MAX_FILES), OpenEditorPayload::files,
            NAME, OpenEditorPayload::entry,
            ComputerSnapshot.STREAM_CODEC, OpenEditorPayload::snapshot,
            OpenEditorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
