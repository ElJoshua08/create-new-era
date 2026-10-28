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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every port, cable span and device name in one dimension. Kept outside the blocks so ports
 * can go on anything, and networks are worked out from the spans when asked for.
 */
public class BusData extends SavedData {
    private static final String ID = "create_new_era_bus";
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    private final Map<Long, BusPort> ports = new LinkedHashMap<>();
    /** span id → the two port ids it joins */
    private final Map<Long, long[]> spans = new LinkedHashMap<>();
    private final Map<BlockPos, String> names = new HashMap<>();
    private long nextId = 1;

    public static BusData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BusData::new, BusData::load, null), ID);
    }

    // ---- ports ----

    /** @return the new port, or null when that spot is taken or off the face */
    public @Nullable BusPort addPort(BlockPos pos, Direction face, int u, int v, PortMode mode) {
        if (u < 0 || u > 15 || v < 0 || v > 15)
            return null;
        for (BusPort p : ports.values())
            if (p.pos().equals(pos) && p.face() == face && p.u() == u && p.v() == v)
                return null;
        BusPort port = new BusPort(nextId++, pos.immutable(), face, u, v, mode);
        ports.put(port.id(), port);
        setDirty();
        return port;
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

    /** Takes the port off, and every span plugged into it. */
    public boolean removePort(long id) {
        if (ports.remove(id) == null)
            return false;
        spans.values().removeIf(s -> s[0] == id || s[1] == id);
        setDirty();
        return true;
    }

    /** The block went away: so do its ports and its name. */
    public void removeAllAt(BlockPos pos) {
        for (BusPort p : portsAt(pos))
            removePort(p.id());
        if (names.remove(pos) != null)
            setDirty();
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

    /** @return the span's id, or null if the ports are missing, the same, or already joined */
    public @Nullable Long connect(long a, long b) {
        if (a == b || !ports.containsKey(a) || !ports.containsKey(b))
            return null;
        for (long[] s : spans.values())
            if ((s[0] == a && s[1] == b) || (s[0] == b && s[1] == a))
                return null;
        long id = nextId++;
        spans.put(id, new long[] {a, b});
        setDirty();
        return id;
    }

    public boolean disconnect(long spanId) {
        boolean removed = spans.remove(spanId) != null;
        if (removed)
            setDirty();
        return removed;
    }

    /** Every port reachable from the given ones through spans, the given ones included. */
    public Set<Long> network(Collection<Long> from) {
        Map<Long, List<Long>> links = new HashMap<>();
        for (long[] s : spans.values()) {
            links.computeIfAbsent(s[0], k -> new ArrayList<>()).add(s[1]);
            links.computeIfAbsent(s[1], k -> new ArrayList<>()).add(s[0]);
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
            portList.add(t);
        }
        tag.put("Ports", portList);
        ListTag spanList = new ListTag();
        spans.forEach((id, s) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", id);
            t.putLong("A", s[0]);
            t.putLong("B", s[1]);
            spanList.add(t);
        });
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
            NbtUtils.readBlockPos(c, "Pos").ifPresent(pos -> data.ports.put(c.getLong("Id"), new BusPort(c.getLong("Id"), pos,
                    Direction.from3DDataValue(c.getByte("Face")), c.getByte("U"), c.getByte("V"),
                    c.getBoolean("Out") ? PortMode.OUT : PortMode.IN)));
        }
        for (Tag t : tag.getList("Spans", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.spans.put(c.getLong("Id"), new long[] {c.getLong("A"), c.getLong("B")});
        }
        for (Tag t : tag.getList("Names", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            NbtUtils.readBlockPos(c, "Pos").ifPresent(pos -> data.names.put(pos, c.getString("Name")));
        }
        return data;
    }
}
