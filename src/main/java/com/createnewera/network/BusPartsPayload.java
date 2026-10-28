package com.createnewera.network;

import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.PortMode;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: every bus part in one chunk, replacing what the client had for it. */
public record BusPartsPayload(long chunk, List<BusPort> parts) implements CustomPacketPayload {
    public static final Type<BusPartsPayload> TYPE = new Type<>(CreateNewEra.asResource("bus_parts"));

    private static final StreamCodec<ByteBuf, BusPort> PART = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.id());
                BlockPos.STREAM_CODEC.encode(buf, p.pos());
                buf.writeByte(p.face().get3DDataValue());
                buf.writeByte(p.u());
                buf.writeByte(p.v());
                buf.writeByte(p.kind().ordinal());
                buf.writeBoolean(p.mode() == PortMode.OUT);
            },
            buf -> new BusPort(buf.readLong(), BlockPos.STREAM_CODEC.decode(buf), Direction.from3DDataValue(buf.readByte()),
                    buf.readByte(), buf.readByte(), BusPort.Kind.values()[buf.readByte()], buf.readBoolean() ? PortMode.OUT : PortMode.IN));

    public static final StreamCodec<FriendlyByteBuf, BusPartsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BusPartsPayload::chunk,
            PART.apply(ByteBufCodecs.list()), BusPartsPayload::parts,
            BusPartsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
