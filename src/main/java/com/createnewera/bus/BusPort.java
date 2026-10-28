package com.createnewera.bus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A port stuck to one face of a block, at any of its 16x16 subpixel spots. It's stored apart
 * from the block, so it can sit on any block (a Create machine included) without changing it.
 */
public record BusPort(long id, BlockPos pos, Direction face, int u, int v, PortMode mode) {
    public BusPort withMode(PortMode newMode) {
        return new BusPort(id, pos, face, u, v, newMode);
    }
}
