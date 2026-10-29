package com.createnewera.cable;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createnewera.bus.BusSpan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The line a cable follows, the same for drawing it and for clicking on it: a hanging span sags
 * in a parabola, a surface one goes straight from bend to bend.
 */
public final class CableShape {
    /** Half the thickness of a bare cable, and of an encased one, in blocks. */
    public static final double BARE = 1.0 / 16, ENCASED = 2.0 / 16;

    public static double radius(BusSpan span) {
        return span.casing() == BusSpan.Casing.NONE ? BARE : ENCASED;
    }

    public static List<Vec3> line(BusSpan span, @Nullable BlockGetter level) {
        return span.aerial() && span.points().size() == 2
                ? hanging(span.points().get(0), span.points().get(1), level, radius(span)) : span.points();
    }

    /**
     * A sagging cable between two points. Where the sag would take it into a block (low ports
     * over a floor, say) it rests on top of that block instead, as a real cable would.
     */
    public static List<Vec3> hanging(Vec3 a, Vec3 b, @Nullable BlockGetter level, double radius) {
        List<Vec3> line = hanging(a, b);
        if (level == null)
            return line;
        for (int i = 1; i < line.size() - 1; i++) {
            Vec3 p = line.get(i);
            BlockPos at = BlockPos.containing(p);
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (shape.isEmpty())
                continue;
            double top = at.getY() + shape.max(Direction.Axis.Y) + radius;
            if (p.y < top)
                line.set(i, new Vec3(p.x, top, p.z));
        }
        return line;
    }

    public static List<Vec3> hanging(Vec3 a, Vec3 b) {
        double length = a.distanceTo(b);
        double flat = Math.hypot(b.x - a.x, b.z - a.z);
        // longer spans sag more; one going straight up hardly at all
        double sag = Math.min(0.15 + length * 0.05, 1.4) * (length < 1e-4 ? 0 : flat / length);
        int n = Math.max(4, (int) Math.ceil(length * 3));
        List<Vec3> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            out.add(a.lerp(b, t).subtract(0, sag * 4 * t * (1 - t), 0));
        }
        return out;
    }

    /** How far along the ray the cable is hit, or -1 if it's missed. */
    public static double hit(BusSpan span, @Nullable BlockGetter level, Vec3 from, Vec3 dir, double reach) {
        double best = -1;
        double r = radius(span) + 0.04;
        List<Vec3> line = line(span, level);
        for (int i = 1; i < line.size(); i++) {
            double t = rayToSegment(from, dir, line.get(i - 1), line.get(i), r);
            if (t >= 0 && t <= reach && (best < 0 || t < best))
                best = t;
        }
        return best;
    }

    /**
     * Distance along a unit ray to where it passes within r of a segment, or -1. The segment is
     * sampled every pixel: cable pieces are short, so this stays cheap and has no edge cases.
     */
    private static double rayToSegment(Vec3 o, Vec3 d, Vec3 a, Vec3 b, double r) {
        double best = -1;
        int steps = Math.max(2, (int) Math.ceil(a.distanceTo(b) * 16));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, (double) i / steps);
            double t = p.subtract(o).dot(d);
            if (t >= 0 && o.add(d.scale(t)).distanceToSqr(p) <= r * r && (best < 0 || t < best))
                best = t;
        }
        return best;
    }

    /** The span the ray hits first, nearer than {@code reach}. */
    public static @Nullable BusSpan pick(Iterable<BusSpan> spans, @Nullable BlockGetter level, Vec3 from, Vec3 dir, double reach) {
        BusSpan best = null;
        double bestT = reach;
        for (BusSpan span : spans) {
            double t = hit(span, level, from, dir, bestT);
            if (t >= 0 && t <= bestT) {
                best = span;
                bestT = t;
            }
        }
        return best;
    }

    private CableShape() {}
}
