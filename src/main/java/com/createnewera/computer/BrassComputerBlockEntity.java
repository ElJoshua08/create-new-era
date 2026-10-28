package com.createnewera.computer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.LuaError;

import com.createnewera.network.ComputerSnapshot;
import com.createnewera.network.ComputerStatePayload;
import com.createnewera.network.EditorActionPayload;
import com.createnewera.network.OpenEditorPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** Holds the program, runs it, and keeps whoever has the editor open up to date. */
public class BrassComputerBlockEntity extends BlockEntity {
    public static final int MAX_SOURCE = ComputerFiles.MAX_FILE;
    /** Per computer per tick. Generous for real work, small enough that runaway loops don't lag the server. */
    static final long BUDGET_NANOS = 1_000_000;

    public static final String DEFAULT_PROGRAM = """
            -- Blinks a redstone signal out of the back.
            -- Edit me, then press Run (Ctrl+R).
            while true do
              redstone.setOutput("back", true)
              sleep(0.5)
              redstone.setOutput("back", false)
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrassComputerBlockEntity computer) {
        computer.tick();
    }

    private void tick() {
        if (running && vm == null)
            start("create_new_era.console.restarted");
        if (vm != null)
            runSlice();
        syncViewers();
    }

    private void runSlice() {
        try {
            LuaComputer.Status status = vm.tick(level.getGameTime(), BUDGET_NANOS);
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
        setRunning(false);
    }

    private void setRunning(boolean value) {
        running = value;
        BlockState state = getBlockState();
        if (state.getValue(BrassComputerBlock.RUNNING) != value)
            level.setBlock(worldPosition, state.setValue(BrassComputerBlock.RUNNING, value), Block.UPDATE_ALL);
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
        return getBlockState().getValue(BrassComputerBlock.FACING);
    }

    private ComputerSnapshot snapshot() {
        List<Integer> in = new ArrayList<>(), out = new ArrayList<>();
        for (ComputerSide side : ComputerSide.values()) {
            in.add(getInput(side));
            out.add(outputs[side.ordinal()]);
        }
        return new ComputerSnapshot(running, getConsole(), in, out);
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

    @Override
    public void setRemoved() {
        super.setRemoved();
        vm = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Disk", files.save());
        tag.putString("Entry", entry);
        tag.putBoolean("Running", running);
        tag.putIntArray("Outputs", outputs);
        ListTag lines = new ListTag();
        console.forEach(line -> lines.add(StringTag.valueOf(line.encode())));
        tag.put("Console", lines);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        public double clock() {
            return (level.getGameTime() - startTick) / 20.0;
        }
    }
}
