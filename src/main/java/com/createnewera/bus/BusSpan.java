package com.createnewera.bus;

import java.util.List;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * A length of cable between two parts (ports or splices).
 *
 * @param color  the dye it was laid with, or null for bare copper, which joins any colour
 * @param aerial hung through the air from end to end; otherwise it runs flat along faces
 * @param points where it runs, in world space: just the two ends for an aerial span, every
 *               bend for a surface one. Kept with the span so a client can draw it without
 *               having the far end's chunk.
 */
public record BusSpan(long id, long a, long b, @Nullable DyeColor color, Casing casing, boolean aerial, List<Vec3> points) {
    public enum Casing {
        NONE, ANDESITE, BRASS, COPPER
    }

    public boolean touches(long port) {
        return a == port || b == port;
    }

    public long other(long port) {
        return a == port ? b : a;
    }

    public BusSpan withColor(@Nullable DyeColor newColor) {
        return new BusSpan(id, a, b, newColor, casing, aerial, points);
    }

    public BusSpan withCasing(Casing newCasing) {
        return new BusSpan(id, a, b, color, newCasing, aerial, points);
    }

    /** How much cable it took, in metres (blocks), rounded up. */
    public int length() {
        return CableRoute.metres(points, aerial);
    }

    /** Two cables meet happily unless both are dyed, and dyed differently. */
    public static boolean compatible(@Nullable DyeColor x, @Nullable DyeColor y) {
        return x == null || y == null || x == y;
    }
}
