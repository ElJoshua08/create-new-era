package com.createnewera.bus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.network.BusPartsPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** The network as one computer sees it, and the rules for changing it. */
public final class Bus {
    /** Ports a computer has room for on its own; each expansion adds as many again. */
    public static final int COMPUTER_PORTS = 4;
    public static final int MAX_EXPANSIONS = 3;

    public static @Nullable BusPort placePort(ServerLevel level, BlockPos pos, Direction face, int u, int v, PortMode mode) {
        return placePart(level, pos, face, u, v, BusPort.Kind.PORT, mode);
    }

    /**
     * Puts a part on a block's face and tells the players who can see it. Computers take a
     * limited number of ports; expansions only go on computers.
     *
     * @return the part, or null if it can't go there
     */
    public static @Nullable BusPort placePart(ServerLevel level, BlockPos pos, Direction face, int u, int v,
                                              BusPort.Kind kind, PortMode mode) {
        if (level.getBlockState(pos).isAir())
            return null;
        BusData data = BusData.get(level);
        boolean computer = level.getBlockState(pos).getBlock() instanceof BrassComputerBlock;
        List<BusPort> here = data.portsAt(pos);
        long ports = here.stream().filter(p -> p.kind() == BusPort.Kind.PORT).count();
        long expansions = here.stream().filter(p -> p.kind() == BusPort.Kind.EXPANSION).count();
        if (kind == BusPort.Kind.EXPANSION && (!computer || expansions >= MAX_EXPANSIONS))
            return null;
        if (kind == BusPort.Kind.PORT && computer && ports >= COMPUTER_PORTS * (1 + expansions))
            return null;
        BusPort part = data.addPart(pos, face, u, v, kind, mode);
        if (part != null)
            sync(level, pos);
        return part;
    }

    /** Takes a part off, with the cables plugged into it. */
    public static void removePart(ServerLevel level, BusPort part) {
        List<BusSpan> cut = BusData.get(level).removePort(part.id());
        if (cut != null)
            syncCut(level, part.pos(), cut);
    }

    /** The wrench: a port's input becomes its output and back. */
    public static void flip(ServerLevel level, BusPort part) {
        if (part.kind() == BusPort.Kind.PORT && BusData.get(level).setMode(part.id(), part.mode().flipped()))
            sync(level, part.pos());
    }

    /** The block a part sat on is gone: so are its parts. */
    public static void blockRemoved(ServerLevel level, BlockPos pos) {
        BusData data = BusData.get(level);
        if (data.portsAt(pos).isEmpty())
            return;
        syncCut(level, pos, data.removeAllAt(pos));
    }

    public enum Laid {
        OK, SAME_PART, TOO_FAR, NO_ROUTE, COLOR_CLASH, ALREADY_JOINED
    }

    /** Where a cable from one part to another would run, or null if it can't. */
    public static @Nullable List<Vec3> route(ServerLevel level, BusPort from, BusPort to, boolean aerial) {
        return aerial ? CableRoute.aerial(from, to) : CableRoute.surface(level, from, to);
    }

    /** A cable worked out before laying it: what's wrong with it, or where it would run. */
    public record Plan(Laid result, List<Vec3> points) {
        static Plan refused(Laid why) {
            return new Plan(why, List.of());
        }
    }

    /** Checks a cable before laying it, so the spool can say what's wrong without spending any. */
    public static Plan plan(ServerLevel level, BusPort from, BusPort to, @Nullable DyeColor color, boolean aerial) {
        if (from.id() == to.id())
            return Plan.refused(Laid.SAME_PART);
        BusData data = BusData.get(level);
        if (!data.accepts(from.id(), color, -1) || !data.accepts(to.id(), color, -1))
            return Plan.refused(Laid.COLOR_CLASH);
        for (BusSpan s : data.spansAt(from.id()))
            if (s.touches(to.id()))
                return Plan.refused(Laid.ALREADY_JOINED);
        List<Vec3> points = route(level, from, to, aerial);
        if (points == null)
            return Plan.refused(aerial ? Laid.TOO_FAR : Laid.NO_ROUTE);
        return new Plan(Laid.OK, points);
    }

    public static @Nullable BusSpan lay(ServerLevel level, BusPort from, BusPort to, @Nullable DyeColor color, boolean aerial, List<Vec3> points) {
        BusSpan span = BusData.get(level).connect(from.id(), to.id(), color, aerial, points);
        if (span != null)
            sync(level, from.pos());
        return span;
    }

    public static @Nullable BusSpan cut(ServerLevel level, long spanId) {
        BusData data = BusData.get(level);
        BusSpan span = data.span(spanId);
        BusPort a = span == null ? null : data.port(span.a());
        if (span == null || data.disconnect(spanId) == null)
            return null;
        if (a != null)
            sync(level, a.pos());
        return span;
    }

    /** A span changed (dye, casing): resend the chunk it's sent with. */
    public static void spanChanged(ServerLevel level, long spanId) {
        BusData data = BusData.get(level);
        BusSpan span = data.span(spanId);
        BusPort a = span == null ? null : data.port(span.a());
        if (a != null)
            sync(level, a.pos());
    }

    /** Resends the chunk a part was in, and the chunks of the cables that went with it. */
    private static void syncCut(ServerLevel level, BlockPos pos, List<BusSpan> cut) {
        BusData data = BusData.get(level);
        Set<Long> chunks = new HashSet<>();
        chunks.add(ChunkPos.asLong(pos));
        for (BusSpan span : cut) {
            BusPort a = data.port(span.a());
            if (a != null)
                chunks.add(ChunkPos.asLong(a.pos()));
        }
        for (long chunk : chunks)
            sync(level, new ChunkPos(chunk));
    }

    /** Sends the chunk's parts to everyone who has it loaded. */
    public static void sync(ServerLevel level, BlockPos pos) {
        sync(level, new ChunkPos(pos));
    }

    private static void sync(ServerLevel level, ChunkPos chunk) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload(level, chunk));
    }

    public static void sendChunk(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        BusPartsPayload payload = payload(level, chunk);
        if (!payload.parts().isEmpty() || !payload.spans().isEmpty())
            PacketDistributor.sendToPlayer(player, payload);
    }

    private static BusPartsPayload payload(ServerLevel level, ChunkPos chunk) {
        BusData data = BusData.get(level);
        return new BusPartsPayload(chunk.toLong(), data.partsInChunk(chunk.toLong()), data.spansInChunk(chunk.toLong()));
    }

    /**
     * Every device reachable from the computer's ports, nearest first. Blocks that have gone
     * away since their ports were placed are cleaned up on the way.
     */
    public static List<BusDevice> devicesSeenFrom(ServerLevel level, BlockPos computer) {
        BusData data = BusData.get(level);
        List<Long> own = data.portsAt(computer).stream().map(BusPort::id).toList();
        Set<Long> reachable = data.network(own);

        Map<BlockPos, boolean[]> modes = new LinkedHashMap<>(); // pos → {readable, writable}
        for (long id : reachable) {
            BusPort port = data.port(id);
            if (port == null || port.pos().equals(computer) || port.kind() != BusPort.Kind.PORT)
                continue;
            boolean[] m = modes.computeIfAbsent(port.pos(), p -> new boolean[2]);
            if (port.mode() == PortMode.OUT)
                m[0] = true;
            else
                m[1] = true;
        }

        List<BusDevice> devices = new ArrayList<>();
        for (var e : modes.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                blockRemoved(level, pos);
                continue;
            }
            String type = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            devices.add(new BusDevice(pos, data.nameOf(pos, state.getBlock()), type, e.getValue()[0], e.getValue()[1],
                    Math.sqrt(pos.distSqr(computer))));
        }
        devices.sort(Comparator.comparingDouble(BusDevice::distance).thenComparing(BusDevice::name));
        return devices;
    }

    public static @Nullable BusDevice find(ServerLevel level, BlockPos computer, String name) {
        for (BusDevice device : devicesSeenFrom(level, computer))
            if (device.name().equals(name))
                return device;
        return null;
    }

    private Bus() {}
}
