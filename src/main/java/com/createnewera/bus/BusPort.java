package com.createnewera.bus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A mini-block stuck to one face of a block: a port, a port expansion, a splice, and later
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
        EXPANSION(6),
        /** Joins cables in the middle of a run; it isn't a device. */
        SPLICE(4),
        /** A panel control's own connection, built into its plate: placed with it, never drawn. */
        CONTROL(4),
        /** A brass data port: read and given orders at once, no mode to pick. */
        BRASS_PORT(4),
        /** One of a port hub's sockets: joined to the hub's others inside it; not a device. */
        HUB(4);

        /** Footprint on the face, in pixels. */
        public final int size;

        Kind(int size) {
            this.size = size;
        }
    }

    /** Ports and splices take cables; expansions don't. */
    public boolean takesCables() {
        return kind != Kind.EXPANSION;
    }

    public BusPort withMode(PortMode newMode) {
        return new BusPort(id, pos, face, u, v, kind, newMode);
    }

    public boolean overlaps(Direction otherFace, int otherU, int otherV, int otherSize) {
        return face == otherFace && u < otherU + otherSize && otherU < u + kind.size && v < otherV + otherSize && otherV < v + kind.size;
    }
}
