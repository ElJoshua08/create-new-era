package com.createnewera.network;

import java.util.ArrayList;
import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.BusSpan;
import com.createnewera.bus.PortMode;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: every bus part in one chunk, and the cables that start there, replacing
 * what the client had for it.
 */
public record BusPartsPayload(long chunk, List<BusPort> parts, List<BusSpan> spans) implements CustomPacketPayload {
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

    private static final StreamCodec<ByteBuf, BusSpan> SPAN = StreamCodec.of(
            (buf, s) -> {
                buf.writeLong(s.id());
                buf.writeLong(s.a());
                buf.writeLong(s.b());
                buf.writeByte(s.color() == null ? -1 : s.color().getId());
                buf.writeByte(s.casing().ordinal());
                buf.writeBoolean(s.aerial());
                buf.writeShort(s.points().size());
                for (Vec3 p : s.points()) {
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                }
            },
            buf -> {
                long id = buf.readLong(), a = buf.readLong(), b = buf.readLong();
                int color = buf.readByte();
                BusSpan.Casing casing = BusSpan.Casing.values()[buf.readByte()];
                boolean aerial = buf.readBoolean();
                int n = buf.readShort();
                List<Vec3> points = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    points.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                return new BusSpan(id, a, b, color < 0 ? null : DyeColor.byId(color), casing, aerial, points);
            });

    public static final StreamCodec<FriendlyByteBuf, BusPartsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BusPartsPayload::chunk,
            PART.apply(ByteBufCodecs.list()), BusPartsPayload::parts,
            SPAN.apply(ByteBufCodecs.list()), BusPartsPayload::spans,
            BusPartsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
