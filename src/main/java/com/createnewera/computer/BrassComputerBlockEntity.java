package com.createnewera.computer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;

import com.createnewera.bus.Bus;
import com.createnewera.bus.BusDevice;
import com.createnewera.bus.DeviceDrivers;
import com.createnewera.bus.LuaValues;
import com.createnewera.network.ComputerSnapshot;
import com.createnewera.network.NetworkDevice;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.createnewera.network.ComputerStatePayload;
import com.createnewera.network.EditorActionPayload;
import com.createnewera.network.OpenEditorPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Holds the files, runs the program, and keeps whoever has the editor open up to date. The
 * program only runs while the computer turns: its slice of each tick grows with the speed.
 */
public class BrassComputerBlockEntity extends KineticBlockEntity {
    public static final int MAX_SOURCE = ComputerFiles.MAX_FILE;
    /**
     * Time per tick at full speed (256 RPM); half the speed gets half the time. Small enough
     * that a room of busy computers doesn't lag the server.
     */
    public static final long MAX_BUDGET_NANOS = 2_000_000;
    static final float FULL_SPEED = 256;

    public static final String DEFAULT_PROGRAM = """
            -- Blinks a redstone signal out of the top: put a lamp on it.
            -- Edit me, then press Run (Ctrl+R).
            while true do
              redstone.setOutput("top", true)
              sleep(0.5)
              redstone.setOutput("top", false)
              sleep(0.5)
            end
            """;

    private final ComputerFiles files = new ComputerFiles();
    /** The file Run last started, which is what restarts when the world loads. */
    private String entry = ComputerFiles.STARTUP;
    /** Saved: a computer that was running when the chunk unloaded starts its program again. */
    private boolean running;
    private final int[] outputs = new int[ComputerSide.values().length];
    private final Deque<ConsoleLine> console = new ArrayDeque<>();

    private @Nullable LuaComputer vm;
    private long startTick;
    private final Set<ServerPlayer> viewers = new HashSet<>();
    private @Nullable ComputerSnapshot lastSent;

    public BrassComputerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    /** Whether its own ports have been put in its sockets since it loaded. */
    private boolean socketsFilled;

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide)
            return;
        if (!socketsFilled) { // computers placed before they came with their own ports
            Bus.fillSockets((ServerLevel) level, worldPosition, getBlockState());
            socketsFilled = true;
        }
        if (running && vm == null)
            start("create_new_era.console.restarted");
        if (vm != null) {
            watchDevices();
            runSlice();
        }
        syncViewers();
    }

    /** Last state seen of each device a handler listens to; a difference wakes its handlers. */
    private final Map<String, Object> deviceSnapshots = new HashMap<>();

    private void watchDevices() {
        Set<String> watched = vm.watched();
        if (watched.isEmpty())
            return;
        ServerLevel server = (ServerLevel) level;
        Map<String, BusDevice> reachable = new HashMap<>();
        for (BusDevice device : Bus.devicesSeenFrom(server, worldPosition))
            reachable.put(device.name(), device);
        for (String name : watched) {
            BusDevice device = reachable.get(name);
            // a device that drops off the network changes too
            Object now = device == null ? "gone" : DeviceDrivers.snapshot(server, device.pos());
            Object before = deviceSnapshots.put(name, now);
            if (before != null && !before.equals(now))
                vm.deviceChanged(name);
        }
    }

    /** How much of this tick the program gets; zero without rotation or when overstressed. */
    public long budgetNanos() {
        float speed = Math.min(Math.abs(getSpeed()), FULL_SPEED);
        return overStressed ? 0 : (long) (MAX_BUDGET_NANOS * speed / FULL_SPEED);
    }

    private void runSlice() {
        long budget = budgetNanos();
        // no rotation: the program waits where it is and picks up when power comes back
        if (budget <= 0)
            return;
        try {
            LuaComputer.Status status = vm.tick(level.getGameTime(), budget);
            if (status == LuaComputer.Status.FINISHED) {
                log(ConsoleLine.Kind.SYSTEM, "create_new_era.console.finished");
                halt();
            }
        } catch (LuaError e) {
            log(ConsoleLine.Kind.ERROR, e.getMessage());
            halt();
        }
    }

    /**
     * @param name the file the action is about (ignored by STOP and CLOSE)
     * @param argument file contents for SAVE, the new name for RENAME
     */
    public void handleAction(@Nullable ServerPlayer player, EditorActionPayload.Action action, String name, String argument) {
        switch (action) {
            case SAVE -> {
                String problem = files.write(name, argument);
                if (problem != null)
                    log(ConsoleLine.Kind.ERROR, name + ": " + problem);
                setChanged();
            }
            case DELETE -> {
                if (files.delete(name))
                    setChanged();
            }
            case RENAME -> {
                if (files.rename(name, argument)) {
                    if (entry.equals(name))
                        entry = argument;
                    setChanged();
                }
            }
            case RUN -> {
                entry = name;
                start("create_new_era.console.running");
            }
            case STOP -> {
                if (running) {
                    log(ConsoleLine.Kind.SYSTEM, "create_new_era.console.stopped");
                    halt();
                    clearOutputs();
                }
            }
            case CLOSE -> viewers.remove(player);
        }
    }

    public void openEditor(ServerPlayer player) {
        viewers.add(player);
        devicesListedAt = level.getGameTime() - 20; // a fresh list for a newly opened editor
        lastSent = snapshot();
        PacketDistributor.sendToPlayer(player, new OpenEditorPayload(worldPosition, files.names(), files.asMap(), entry, lastSent));
    }

    /** Starts {@link #entry}. @param reason lang key for the console line announcing the start */
    public void start(String reason) {
        halt();
        log(ConsoleLine.Kind.SYSTEM, reason);
        String source = files.read(entry);
        if (source == null) {
            log(ConsoleLine.Kind.ERROR, "no file named " + entry);
            return;
        }
        try {
            vm = LuaComputer.start(source, entry, new Host());
            startTick = level.getGameTime();
            setRunning(true);
        } catch (LuaError e) {
            log(ConsoleLine.Kind.ERROR, e.getMessage());
        }
    }

    private void halt() {
        vm = null;
        deviceSnapshots.clear();
        setRunning(false);
    }

    private void setRunning(boolean value) {
        running = value;
        BlockState state = getBlockState();
        // Create's way of changing a kinetic block's state without dropping it off its network
        if (state.getValue(BrassComputerBlock.RUNNING) != value)
            KineticBlockEntity.switchToBlockState(level, worldPosition, state.setValue(BrassComputerBlock.RUNNING, value));
        setChanged();
    }

    public boolean isRunning() {
        return running;
    }

    public ComputerFiles getFiles() {
        return files;
    }

    /** Writes a file and makes it the one Run and restarts use. */
    public void setProgram(String name, String source) {
        files.write(name, source);
        entry = name;
        setChanged();
    }

    public List<ConsoleLine> getConsole() {
        return List.copyOf(console);
    }

    public void log(ConsoleLine.Kind kind, String text) {
        for (String line : text.split("\n", -1)) {
            console.addLast(new ConsoleLine(kind, line));
            while (console.size() > ComputerSnapshot.MAX_CONSOLE)
                console.removeFirst();
        }
        setChanged();
    }

    /** Signal this computer sends out of the given world direction. */
    public int getOutput(Direction direction) {
        return outputs[ComputerSide.fromDirection(facing(), direction).ordinal()];
    }

    private void setOutput(ComputerSide side, int strength) {
        if (outputs[side.ordinal()] == strength)
            return;
        outputs[side.ordinal()] = strength;
        setChanged();
        Direction direction = side.toDirection(facing());
        Block block = getBlockState().getBlock();
        level.updateNeighborsAt(worldPosition, block);
        // strong power: also reach whatever sits behind the block we're powering
        level.updateNeighborsAtExceptFromFacing(worldPosition.relative(direction), block, direction.getOpposite());
    }

    private void clearOutputs() {
        for (ComputerSide side : ComputerSide.values())
            setOutput(side, 0);
    }

    private int getInput(ComputerSide side) {
        Direction direction = side.toDirection(facing());
        return level.getSignal(worldPosition.relative(direction), direction);
    }

    private Direction facing() {
        return getBlockState().getValue(BrassComputerBlock.HORIZONTAL_FACING);
    }

    private ComputerSnapshot snapshot() {
        List<Integer> in = new ArrayList<>(), out = new ArrayList<>();
        for (ComputerSide side : ComputerSide.values()) {
            in.add(getInput(side));
            out.add(outputs[side.ordinal()]);
        }
        return new ComputerSnapshot(running, getConsole(), in, out, Math.round(getSpeed()), overStressed, networkDevices());
    }

    private List<NetworkDevice> devicesForEditor = List.of();
    private long devicesListedAt = -20;

    /** The network as the editor's suggestions need it, worked out again once a second. */
    private List<NetworkDevice> networkDevices() {
        long now = level.getGameTime();
        if (now - devicesListedAt < 20)
            return devicesForEditor;
        devicesListedAt = now;
        ServerLevel server = (ServerLevel) level;
        List<NetworkDevice> out = new ArrayList<>();
        for (BusDevice device : Bus.devicesSeenFrom(server, worldPosition)) {
            if (out.size() == ComputerSnapshot.MAX_DEVICES)
                break;
            // what its ports let a program do, with what the editor shows for each
            List<NetworkDevice.Fn> functions = new ArrayList<>();
            for (DeviceDrivers.Fn fn : DeviceDrivers.describe(server, device.pos(), device.readable(), device.writable()))
                functions.add(new NetworkDevice.Fn(fn.name(), fn.params(), fn.returns(), fn.doc(), fn.reads()));
            String typeKey = level.getBlockState(device.pos()).getBlock().getDescriptionId();
            out.add(new NetworkDevice(device.name(), device.type(), typeKey, device.pos(), (int) Math.round(device.distance()),
                    device.readable(), device.writable(), functions));
        }
        devicesForEditor = List.copyOf(out);
        return devicesForEditor;
    }

    private void syncViewers() {
        if (viewers.isEmpty())
            return;
        viewers.removeIf(p -> p.isRemoved() || !p.canInteractWithBlock(worldPosition, 4));
        ComputerSnapshot now = snapshot();
        if (now.equals(lastSent))
            return;
        lastSent = now;
        var payload = new ComputerStatePayload(worldPosition, now);
        for (ServerPlayer player : viewers)
            PacketDistributor.sendToPlayer(player, payload);
    }

    /** Under goggles: Create's stress figures, then what program it runs and how fast it's going. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable("create_new_era.goggles.computer")));
        if (!running) {
            tooltip.add(Component.literal("    ").append(Component.translatable("create_new_era.goggles.idle")).withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }
        tooltip.add(Component.literal("    ").append(Component.translatable("create_new_era.goggles.program", entry)).withStyle(ChatFormatting.GRAY));
        Component pace = getSpeed() == 0
                ? Component.translatable(overStressed ? "create_new_era.goggles.paused_stress" : "create_new_era.goggles.paused_rotation").withStyle(ChatFormatting.GOLD)
                : Component.translatable("create_new_era.goggles.pace", Math.round(100 * Math.min(1, Math.abs(getSpeed()) / FULL_SPEED))).withStyle(ChatFormatting.GRAY);
        tooltip.add(Component.literal("    ").append(pace));
        return true;
    }

    /** Broken or unloaded: the program goes with it (a saved running flag restarts it on load). */
    @Override
    public void invalidate() {
        super.invalidate();
        vm = null;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Entry", entry);
        tag.putBoolean("Running", running);
        // the disk and console reach editors through their own packets, not chunk updates
        if (clientPacket)
            return;
        tag.put("Disk", files.save());
        tag.putIntArray("Outputs", outputs);
        ListTag lines = new ListTag();
        console.forEach(line -> lines.add(StringTag.valueOf(line.encode())));
        tag.put("Console", lines);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) { // for the goggles
            entry = tag.getString("Entry");
            running = tag.getBoolean("Running");
            return;
        }
        if (tag.contains("Disk"))
            files.load(tag.getCompound("Disk"));
        else if (tag.contains("Source")) // single-program computers from before files existed
            files.write(ComputerFiles.STARTUP, tag.getString("Source"));
        entry = tag.contains("Entry") ? tag.getString("Entry") : ComputerFiles.STARTUP;
        running = tag.getBoolean("Running");
        int[] saved = tag.getIntArray("Outputs");
        Arrays.fill(outputs, 0);
        System.arraycopy(saved, 0, outputs, 0, Math.min(saved.length, outputs.length));
        console.clear();
        for (Tag line : tag.getList("Console", Tag.TAG_STRING))
            console.addLast(ConsoleLine.decode(line.getAsString()));
    }

    private class Host implements LuaComputer.Host {
        @Override
        public void print(String line) {
            log(ConsoleLine.Kind.OUTPUT, line);
        }

        @Override
        public int getInput(ComputerSide side) {
            return BrassComputerBlockEntity.this.getInput(side);
        }

        @Override
        public int getOutput(ComputerSide side) {
            return outputs[side.ordinal()];
        }

        @Override
        public void setOutput(ComputerSide side, int strength) {
            BrassComputerBlockEntity.this.setOutput(side, strength);
        }

        @Override
        public @Nullable String[] readFile(String module) {
            String name = files.resolve(module);
            return name == null ? null : new String[] {name, files.read(name)};
        }

        @Override
        public float speed() {
            return getSpeed();
        }

        @Override
        public double clock() {
            return (level.getGameTime() - startTick) / 20.0;
        }

        @Override
        public long now() {
            return level.getGameTime();
        }

        @Override
        public LuaValue busList() {
            LuaTable list = new LuaTable();
            int i = 1;
            for (BusDevice device : Bus.devicesSeenFrom((ServerLevel) level, worldPosition)) {
                LuaTable entry = new LuaTable();
                entry.rawset("name", ValueFactory.valueOf(device.name()));
                entry.rawset("type", ValueFactory.valueOf(device.type()));
                entry.rawset("distance", ValueFactory.valueOf(Math.round(device.distance() * 10) / 10.0));
                entry.rawset("readable", ValueFactory.valueOf(device.readable()));
                entry.rawset("writable", ValueFactory.valueOf(device.writable()));
                list.rawset(i++, entry);
            }
            return list;
        }

        @Override
        public LuaValue busInfo(String name) throws LuaError {
            BusDevice device = reach(name);
            LuaTable methods = new LuaTable();
            int i = 1;
            // every method the device has, allowed or not: calling one its ports don't allow says which port is missing
            for (String method : DeviceDrivers.methods((ServerLevel) level, device.pos(), true, true).keySet())
                methods.rawset(i++, ValueFactory.valueOf(method));
            LuaTable info = new LuaTable();
            info.rawset("name", ValueFactory.valueOf(device.name()));
            info.rawset("type", ValueFactory.valueOf(device.type()));
            info.rawset("methods", methods);
            return info;
        }

        @Override
        public Varargs busCall(String name, String method, Varargs args) throws LuaError {
            BusDevice device = reach(name);
            ServerLevel server = (ServerLevel) level;
            DeviceDrivers.Method call = DeviceDrivers.methods(server, device.pos(), device.readable(), device.writable()).get(method);
            if (call == null) {
                // say which port is missing rather than pretending the method doesn't exist
                boolean exists = DeviceDrivers.methods(server, device.pos(), true, true).containsKey(method);
                throw new LuaError(exists
                        ? name + "." + method + " needs " + (device.readable() ? "an input" : "an output") + " port on " + name
                        : name + " has no " + method);
            }
            return LuaValues.of(call.call(args));
        }

        private BusDevice reach(String name) throws LuaError {
            BusDevice device = Bus.find((ServerLevel) level, worldPosition, name);
            if (device == null)
                throw new LuaError("no device named '" + name + "' on this computer's network");
            return device;
        }
    }
}
