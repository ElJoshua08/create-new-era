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
     * Puts a part on a block's face and tells the players who can see it. A computer's first
     * ports go in its sockets (snapping to the nearest free one on the face), more need
     * expansions, and nothing else may cover a socket; expansions only go on computers.
     *
     * @return the part, or null if it can't go there
     */
    public static @Nullable BusPort placePart(ServerLevel level, BlockPos pos, Direction face, int u, int v,
                                              BusPort.Kind kind, PortMode mode) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return null;
        BusData data = BusData.get(level);
        boolean computer = state.getBlock() instanceof BrassComputerBlock;
        if (kind == BusPort.Kind.EXPANSION && !computer)
            return null;
        if (computer) {
            List<BusPort> here = data.portsAt(pos);
            long ports = here.stream().filter(p -> p.kind() == BusPort.Kind.PORT).count();
            long expansions = here.stream().filter(p -> p.kind() == BusPort.Kind.EXPANSION).count();
            List<BrassComputerBlock.Socket> sockets = BrassComputerBlock.sockets(state, pos);
            List<BrassComputerBlock.Socket> free = sockets.stream().filter(s -> here.stream().noneMatch(s::takenBy)).toList();
            if (kind == BusPort.Kind.EXPANSION && expansions >= MAX_EXPANSIONS)
                return null;
            if (kind == BusPort.Kind.PORT) {
                if (ports >= COMPUTER_PORTS * (1 + expansions))
                    return null;
                int askedU = u, askedV = v;
                BrassComputerBlock.Socket socket = free.stream().filter(s -> s.face() == face)
                        .min(Comparator.comparingInt(s -> Math.abs(s.u() - askedU) + Math.abs(s.v() - askedV))).orElse(null);
                if (socket != null) {
                    u = socket.u();
                    v = socket.v();
                } else if (!free.isEmpty()) {
                    return null; // the sockets come first
                }
            }
            BrassComputerBlock.Socket spot = new BrassComputerBlock.Socket(face, u, v);
            boolean inSocket = kind == BusPort.Kind.PORT && sockets.contains(spot);
            if (!inSocket && sockets.stream().anyMatch(s -> s.overlaps(spot, kind.size)))
                return null; // keep the sockets clear
        }
        BusPort part = data.addPart(pos, face, u, v, kind, mode);
        if (part != null)
            sync(level, pos);
        return part;
    }

    /** Gives a panel control its built-in connection, once. */
    public static void fitControl(ServerLevel level, BlockPos pos, Direction facing) {
        BusData data = BusData.get(level);
        if (data.portsAt(pos).stream().anyMatch(p -> p.kind() == BusPort.Kind.CONTROL))
            return;
        if (data.addPart(pos, facing, 6, 6, BusPort.Kind.CONTROL, PortMode.IN) != null)
            sync(level, pos);
    }

    /** A panel control's connection, if the block has one. */
    public static @Nullable BusPort controlAt(List<BusPort> parts) {
        for (BusPort part : parts)
            if (part.kind() == BusPort.Kind.CONTROL)
                return part;
        return null;
    }

    /**
     * Whether a part is one of a computer's own ports, sitting in one of its sockets: those come
     * with the computer and can't be taken off.
     */
    public static boolean builtIn(BlockState state, BusPort part) {
        if (part.kind() == BusPort.Kind.CONTROL)
            return true;
        return part.kind() == BusPort.Kind.PORT && state.getBlock() instanceof BrassComputerBlock
                && BrassComputerBlock.sockets(state, part.pos()).stream()
                        .anyMatch(s -> s.face() == part.face() && s.u() == part.u() && s.v() == part.v());
    }

    /**
     * Puts a computer's own ports in whichever of its sockets are missing one. A port already
     * overlapping an empty socket (left where the sockets used to be) is moved into it, cables
     * and all, rather than doubled up.
     */
    public static void fillSockets(ServerLevel level, BlockPos pos, BlockState state) {
        BusData data = BusData.get(level);
        boolean changed = false;
        List<BusSpan> moved = new ArrayList<>();
        for (BrassComputerBlock.Socket socket : BrassComputerBlock.sockets(state, pos)) {
            List<BusPort> here = data.portsAt(pos);
            if (here.stream().anyMatch(p -> p.kind() == BusPort.Kind.PORT && p.face() == socket.face()
                    && p.u() == socket.u() && p.v() == socket.v()))
                continue;
            BusPort stray = here.stream().filter(socket::takenBy).findFirst().orElse(null);
            if (stray != null && stray.kind() == BusPort.Kind.PORT
                    && here.stream().filter(p -> p != stray).noneMatch(socket::takenBy)) {
                BusPort into = data.movePart(stray.id(), socket.face(), socket.u(), socket.v());
                for (BusSpan span : data.spansAt(stray.id())) {
                    if (span.aerial())
                        data.moveSpanEnd(span.id(), stray.id(), PartGeometry.anchor(into));
                    else
                        data.disconnect(span.id());
                    moved.add(span);
                }
                changed = true;
            } else if (stray == null) {
                changed |= data.addPart(pos, socket.face(), socket.u(), socket.v(), BusPort.Kind.PORT, PortMode.IN) != null;
            }
        }
        if (changed)
            syncCut(level, pos, moved);
    }

    /**
     * The computer turned: its own ports turn with it, socket for socket. Hanging cables follow
     * them; cables laid flat along faces no longer fit their route, so they're cut.
     */
    public static void turnSockets(ServerLevel level, BlockPos pos, BlockState from, BlockState to) {
        BusData data = BusData.get(level);
        List<BrassComputerBlock.Socket> before = BrassComputerBlock.sockets(from, pos);
        List<BrassComputerBlock.Socket> after = BrassComputerBlock.sockets(to, pos);
        List<BusSpan> cut = new ArrayList<>();
        for (BusPort part : data.portsAt(pos)) {
            if (part.kind() != BusPort.Kind.PORT)
                continue;
            for (int i = 0; i < before.size(); i++) {
                BrassComputerBlock.Socket old = before.get(i), now = after.get(i);
                if (old.face() != part.face() || old.u() != part.u() || old.v() != part.v())
                    continue;
                BusPort moved = data.movePart(part.id(), now.face(), now.u(), now.v());
                for (BusSpan span : data.spansAt(part.id())) {
                    if (span.aerial())
                        data.moveSpanEnd(span.id(), part.id(), PartGeometry.anchor(moved));
                    else
                        data.disconnect(span.id());
                    cut.add(span); // its chunk gets resent either way
                }
            }
        }
        syncCut(level, pos, cut);
        fillSockets(level, pos, to);
    }

    /** Why a part couldn't go on a computer, for telling the player. */
    public static String refusalOnComputer(ServerLevel level, BlockPos pos, BusPort.Kind kind) {
        if (kind != BusPort.Kind.PORT)
            return "create_new_era.bus.no_room";
        List<BusPort> here = BusData.get(level).portsAt(pos);
        boolean socketsLeft = BrassComputerBlock.sockets(level.getBlockState(pos), pos).stream()
                .anyMatch(s -> here.stream().noneMatch(s::takenBy));
        return socketsLeft ? "create_new_era.bus.use_sockets" : "create_new_era.bus.computer_full";
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
            if (port == null || port.pos().equals(computer) || (port.kind() != BusPort.Kind.PORT && port.kind() != BusPort.Kind.CONTROL))
                continue;
            boolean[] m = modes.computeIfAbsent(port.pos(), p -> new boolean[2]);
            if (port.kind() == BusPort.Kind.CONTROL) {
                m[0] = m[1] = true; // read and given orders through the same connection
            } else if (port.mode() == PortMode.OUT)
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
