package com.createnewera.bus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A mini-block stuck to one face of a block: a port, a port expansion, and later splices and
 * antennas. It's stored apart from the block, so it can sit on any block (a Create machine
 * included) without changing it.
 *
 * @param u    left edge on the face's 16x16 grid, as the part sees the face when it points up
 * @param v    top edge, likewise
 * @param mode which way a port faces; ignored by other kinds
 */
public record BusPort(long id, BlockPos pos, Direction face, int u, int v, Kind kind, PortMode mode) {
    public enum Kind {
        PORT(4),
        /** Gives a computer more ports. */
        EXPANSION(6);

        /** Footprint on the face, in pixels. */
        public final int size;

        Kind(int size) {
            this.size = size;
        }
    }

    public BusPort withMode(PortMode newMode) {
        return new BusPort(id, pos, face, u, v, kind, newMode);
    }

    public boolean overlaps(Direction otherFace, int otherU, int otherV, int otherSize) {
        return face == otherFace && u < otherU + otherSize && otherU < u + kind.size && v < otherV + otherSize && otherV < v + kind.size;
    }
}
