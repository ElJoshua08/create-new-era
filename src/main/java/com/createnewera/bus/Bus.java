package com.createnewera.bus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createnewera.computer.BrassComputerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** The network as one computer sees it, and the rules for changing it. */
public final class Bus {
    /** Ports a computer has room for before it needs a port expansion. */
    public static final int COMPUTER_PORTS = 4;

    /**
     * Puts a port on a block's face. Computers take a limited number of ports.
     *
     * @return the port, or null if it can't go there
     */
    public static @Nullable BusPort placePort(ServerLevel level, BlockPos pos, Direction face, int u, int v, PortMode mode) {
        if (level.getBlockState(pos).isAir())
            return null;
        BusData data = BusData.get(level);
        if (level.getBlockState(pos).getBlock() instanceof BrassComputerBlock && data.portsAt(pos).size() >= COMPUTER_PORTS)
            return null;
        return data.addPort(pos, face, u, v, mode);
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
            if (port == null || port.pos().equals(computer))
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
                data.removeAllAt(pos);
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
