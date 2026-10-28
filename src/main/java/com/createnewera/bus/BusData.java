package com.createnewera.bus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Every port, cable span and device name in one dimension. Kept outside the blocks so ports
 * can go on anything, and networks are worked out from the spans when asked for.
 */
public class BusData extends SavedData {
    private static final String ID = "create_new_era_bus";
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    private final Map<Long, BusPort> ports = new LinkedHashMap<>();
    private final Map<Long, BusSpan> spans = new LinkedHashMap<>();
    private final Map<BlockPos, String> names = new HashMap<>();
    /** chunk → ids of the parts in it */
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private long nextId = 1;

    public static BusData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BusData::new, BusData::load, null), ID);
    }

    // ---- ports ----

    /** @return the new part, or null when it would overlap another or hang off the face */
    public @Nullable BusPort addPart(BlockPos pos, Direction face, int u, int v, BusPort.Kind kind, PortMode mode) {
        if (u < 0 || v < 0 || u + kind.size > 16 || v + kind.size > 16)
            return null;
        for (BusPort p : portsAt(pos))
            if (p.overlaps(face, u, v, kind.size))
                return null;
        BusPort port = new BusPort(nextId++, pos.immutable(), face, u, v, kind, mode);
        ports.put(port.id(), port);
        index(port);
        setDirty();
        return port;
    }

    public @Nullable BusPort addPort(BlockPos pos, Direction face, int u, int v, PortMode mode) {
        return addPart(pos, face, u, v, BusPort.Kind.PORT, mode);
    }

    /** Parts in one chunk, for sending to the players who can see it. */
    public List<BusPort> partsInChunk(long chunk) {
        List<BusPort> out = new ArrayList<>();
        for (long id : byChunk.getOrDefault(chunk, Set.of()))
            out.add(ports.get(id));
        return out;
    }

    private void index(BusPort port) {
        byChunk.computeIfAbsent(ChunkPos.asLong(port.pos()), k -> new HashSet<>()).add(port.id());
    }

    public @Nullable BusPort port(long id) {
        return ports.get(id);
    }

    public List<BusPort> portsAt(BlockPos pos) {
        List<BusPort> out = new ArrayList<>();
        for (BusPort p : ports.values())
            if (p.pos().equals(pos))
                out.add(p);
        return out;
    }

    /**
     * Takes the part off, and every span plugged into it.
     *
     * @return the spans that went with it, or null if there was no such part
     */
    public @Nullable List<BusSpan> removePort(long id) {
        BusPort removed = ports.remove(id);
        if (removed == null)
            return null;
        Set<Long> chunk = byChunk.get(ChunkPos.asLong(removed.pos()));
        if (chunk != null)
            chunk.remove(id);
        List<BusSpan> cut = spansAt(id);
        for (BusSpan span : cut)
            spans.remove(span.id());
        setDirty();
        return cut;
    }

    /**
     * The block went away: so do its ports and its name.
     *
     * @return the spans cut on the way
     */
    public List<BusSpan> removeAllAt(BlockPos pos) {
        List<BusSpan> cut = new ArrayList<>();
        for (BusPort p : portsAt(pos))
            cut.addAll(removePort(p.id()));
        if (names.remove(pos) != null)
            setDirty();
        return cut;
    }

    public boolean setMode(long id, PortMode mode) {
        BusPort port = ports.get(id);
        if (port == null)
            return false;
        ports.put(id, port.withMode(mode));
        setDirty();
        return true;
    }

    // ---- spans ----

    /**
     * Lays a span. Both ends must take cables and accept its colour: a part that already has a
     * dyed cable only takes that colour, or bare copper.
     *
     * @return the span, or null if the parts are missing, the same, already joined, or clash
     */
    public @Nullable BusSpan connect(long a, long b, @Nullable DyeColor color, boolean aerial, List<Vec3> points) {
        if (a == b || !ports.containsKey(a) || !ports.containsKey(b))
            return null;
        if (!ports.get(a).takesCables() || !ports.get(b).takesCables())
            return null;
        if (!accepts(a, color, -1) || !accepts(b, color, -1))
            return null;
        for (BusSpan s : spans.values())
            if (s.touches(a) && s.touches(b))
                return null;
        BusSpan span = new BusSpan(nextId++, a, b, color, BusSpan.Casing.NONE, aerial, List.copyOf(points));
        spans.put(span.id(), span);
        setDirty();
        return span;
    }

    /** A bare hanging span straight between two parts, for tests and old saves. */
    public @Nullable BusSpan connect(long a, long b) {
        if (!ports.containsKey(a) || !ports.containsKey(b))
            return null;
        return connect(a, b, null, true, List.of(PartGeometry.anchor(ports.get(a)), PartGeometry.anchor(ports.get(b))));
    }

    /** @return the span taken out, or null if there was none */
    public @Nullable BusSpan disconnect(long spanId) {
        BusSpan removed = spans.remove(spanId);
        if (removed != null)
            setDirty();
        return removed;
    }

    public @Nullable BusSpan span(long id) {
        return spans.get(id);
    }

    public List<BusSpan> spansAt(long port) {
        List<BusSpan> out = new ArrayList<>();
        for (BusSpan s : spans.values())
            if (s.touches(port))
                out.add(s);
        return out;
    }

    /** The colour a part's cables have fixed for it, or null while it only has bare ones (or none). */
    public @Nullable DyeColor colorAt(long port, long ignoringSpan) {
        for (BusSpan s : spans.values())
            if (s.touches(port) && s.id() != ignoringSpan && s.color() != null)
                return s.color();
        return null;
    }

    /** Whether a cable of this colour can plug into the part, leaving one span out of the count. */
    public boolean accepts(long port, @Nullable DyeColor color, long ignoringSpan) {
        return BusSpan.compatible(colorAt(port, ignoringSpan), color);
    }

    /** Re-dyes a span; refused if either end already has cables of another colour. */
    public boolean dye(long spanId, @Nullable DyeColor color) {
        BusSpan span = spans.get(spanId);
        if (span == null || span.color() == color || !accepts(span.a(), color, spanId) || !accepts(span.b(), color, spanId))
            return false;
        spans.put(spanId, span.withColor(color));
        setDirty();
        return true;
    }

    public boolean encase(long spanId, BusSpan.Casing casing) {
        BusSpan span = spans.get(spanId);
        if (span == null || span.casing() == casing)
            return false;
        spans.put(spanId, span.withCasing(casing));
        setDirty();
        return true;
    }

    /** Spans whose first end is in this chunk: each span is sent with that chunk only. */
    public List<BusSpan> spansInChunk(long chunk) {
        List<BusSpan> out = new ArrayList<>();
        for (BusSpan s : spans.values()) {
            BusPort a = ports.get(s.a());
            if (a != null && ChunkPos.asLong(a.pos()) == chunk)
                out.add(s);
        }
        return out;
    }

    /**
     * Every part reachable from the given ones through spans, the given ones included. Colours
     * never need checking here: a part can't hold two clashing colours, so whatever a span
     * reaches is on its network.
     */
    public Set<Long> network(Collection<Long> from) {
        Map<Long, List<Long>> links = new HashMap<>();
        for (BusSpan s : spans.values()) {
            links.computeIfAbsent(s.a(), k -> new ArrayList<>()).add(s.b());
            links.computeIfAbsent(s.b(), k -> new ArrayList<>()).add(s.a());
        }
        Set<Long> seen = new HashSet<>();
        Deque<Long> todo = new ArrayDeque<>();
        for (long id : from)
            if (ports.containsKey(id) && seen.add(id))
                todo.add(id);
        while (!todo.isEmpty())
            for (long next : links.getOrDefault(todo.poll(), List.of()))
                if (seen.add(next))
                    todo.add(next);
        return seen;
    }

    // ---- names ----

    /**
     * The device's name, giving it one the first time it's asked for: the block's id
     * ("mechanical_press"), with a number when that's taken ("mechanical_press_2").
     */
    public String nameOf(BlockPos pos, Block block) {
        String name = names.get(pos);
        if (name != null)
            return name;
        String base = BuiltInRegistries.BLOCK.getKey(block).getPath();
        name = base;
        for (int i = 2; names.containsValue(name); i++)
            name = base + "_" + i;
        names.put(pos.immutable(), name);
        setDirty();
        return name;
    }

    /** @return false if the name is malformed or another device already has it */
    public boolean rename(BlockPos pos, String name) {
        if (!NAME.matcher(name).matches())
            return false;
        BlockPos owner = byName(name);
        if (owner != null && !owner.equals(pos))
            return false;
        names.put(pos.immutable(), name);
        setDirty();
        return true;
    }

    public @Nullable BlockPos byName(String name) {
        for (var e : names.entrySet())
            if (e.getValue().equals(name))
                return e.getKey();
        return null;
    }

    // ---- saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextId", nextId);
        ListTag portList = new ListTag();
        for (BusPort p : ports.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", p.id());
            t.put("Pos", NbtUtils.writeBlockPos(p.pos()));
            t.putByte("Face", (byte) p.face().get3DDataValue());
            t.putByte("U", (byte) p.u());
            t.putByte("V", (byte) p.v());
            t.putBoolean("Out", p.mode() == PortMode.OUT);
            t.putString("Kind", p.kind().name());
            portList.add(t);
        }
        tag.put("Ports", portList);
        ListTag spanList = new ListTag();
        for (BusSpan s : spans.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", s.id());
            t.putLong("A", s.a());
            t.putLong("B", s.b());
            if (s.color() != null)
                t.putString("Color", s.color().getSerializedName());
            if (s.casing() != BusSpan.Casing.NONE)
                t.putString("Casing", s.casing().name());
            t.putBoolean("Aerial", s.aerial());
            ListTag points = new ListTag();
            for (Vec3 p : s.points()) {
                points.add(DoubleTag.valueOf(p.x));
                points.add(DoubleTag.valueOf(p.y));
                points.add(DoubleTag.valueOf(p.z));
            }
            t.put("Points", points);
            spanList.add(t);
        }
        tag.put("Spans", spanList);
        ListTag nameList = new ListTag();
        names.forEach((pos, name) -> {
            CompoundTag t = new CompoundTag();
            t.put("Pos", NbtUtils.writeBlockPos(pos));
            t.putString("Name", name);
            nameList.add(t);
        });
        tag.put("Names", nameList);
        return tag;
    }

    private static BusData load(CompoundTag tag, HolderLookup.Provider registries) {
        BusData data = new BusData();
        data.nextId = tag.getLong("NextId");
        for (Tag t : tag.getList("Ports", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BusPort.Kind kind = c.contains("Kind") ? BusPort.Kind.valueOf(c.getString("Kind")) : BusPort.Kind.PORT;
            NbtUtils.readBlockPos(c, "Pos").ifPresent(pos -> {
                BusPort port = new BusPort(c.getLong("Id"), pos, Direction.from3DDataValue(c.getByte("Face")),
                        c.getByte("U"), c.getByte("V"), kind, c.getBoolean("Out") ? PortMode.OUT : PortMode.IN);
                data.ports.put(port.id(), port);
                data.index(port);
            });
        }
        for (Tag t : tag.getList("Spans", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            long a = c.getLong("A"), b = c.getLong("B");
            if (!data.ports.containsKey(a) || !data.ports.containsKey(b))
                continue;
            ListTag raw = c.getList("Points", Tag.TAG_DOUBLE);
            List<Vec3> points = new ArrayList<>();
            for (int i = 0; i + 2 < raw.size(); i += 3)
                points.add(new Vec3(raw.getDouble(i), raw.getDouble(i + 1), raw.getDouble(i + 2)));
            if (points.size() < 2) // saved before cables had routes: hang it straight
                points = List.of(PartGeometry.anchor(data.ports.get(a)), PartGeometry.anchor(data.ports.get(b)));
            DyeColor color = c.contains("Color") ? DyeColor.byName(c.getString("Color"), null) : null;
            BusSpan.Casing casing = c.contains("Casing") ? BusSpan.Casing.valueOf(c.getString("Casing")) : BusSpan.Casing.NONE;
            boolean aerial = !c.contains("Aerial") || c.getBoolean("Aerial");
            data.spans.put(c.getLong("Id"), new BusSpan(c.getLong("Id"), a, b, color, casing, aerial, List.copyOf(points)));
        }
        for (Tag t : tag.getList("Names", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            NbtUtils.readBlockPos(c, "Pos").ifPresent(pos -> data.names.put(pos, c.getString("Name")));
        }
        return data;
    }
}
