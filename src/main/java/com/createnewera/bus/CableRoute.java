package com.createnewera.bus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Where a cable goes between two parts: straight through the air, or crawling along the faces of
 * blocks. A surface route walks cells: an open block space plus the solid face it lies on, and
 * from each cell it can go flat, up a wall in front of it, or round the edge it's sitting on.
 */
public final class CableRoute {
    /** Longest hanging span, in blocks. */
    public static final double MAX_AERIAL = 24;
    /** Longest surface route, in cells. */
    public static final int MAX_SURFACE = 64;
    /** Cells looked at before giving up on a surface route. */
    private static final int SEARCH_LIMIT = 6000;
    /** A surface cable lies this far off the face, so it never fights the face for its pixels. */
    private static final double LIFT = 1.0 / 16;

    /** A hanging span: just its two ends; the sag is drawn, not stored. */
    public static @Nullable List<Vec3> aerial(BusPort from, BusPort to) {
        Vec3 a = PartGeometry.anchor(from), b = PartGeometry.anchor(to);
        return a.distanceTo(b) <= MAX_AERIAL ? List.of(a, b) : null;
    }

    private record Cell(BlockPos pos, Direction face) {}

    /**
     * The shortest run along faces from one part to the other, or null when the faces they sit
     * on aren't joined by open surface (or it's too long).
     */
    public static @Nullable List<Vec3> surface(BlockGetter level, BusPort from, BusPort to) {
        Cell start = new Cell(from.pos().relative(from.face()), from.face().getOpposite());
        Cell goal = new Cell(to.pos().relative(to.face()), to.face().getOpposite());
        if (!open(level, start.pos) || !open(level, goal.pos))
            return null;
        Map<Cell, Cell> came = new HashMap<>();
        Map<Cell, Integer> steps = new HashMap<>();
        Deque<Cell> todo = new ArrayDeque<>();
        came.put(start, start);
        steps.put(start, 0);
        todo.add(start);
        while (!todo.isEmpty() && came.size() < SEARCH_LIMIT) {
            Cell cell = todo.poll();
            if (cell.equals(goal))
                break;
            int n = steps.get(cell);
            if (n >= MAX_SURFACE)
                continue;
            for (Cell next : neighbours(level, cell))
                if (!came.containsKey(next)) {
                    came.put(next, cell);
                    steps.put(next, n + 1);
                    todo.add(next);
                }
        }
        if (!came.containsKey(goal))
            return null;

        List<Cell> cells = new ArrayList<>();
        for (Cell c = goal; ; c = came.get(c)) {
            cells.add(0, c);
            if (c.equals(start))
                break;
        }
        List<Vec3> points = new ArrayList<>();
        points.add(onFace(PartGeometry.anchor(from), from.face()));
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            if (i > 0) {
                Cell prev = cells.get(i - 1);
                if (prev.face != c.face)
                    points.add(bend(prev, c));
            }
            // the middle of the face, unless it's an end: there the cable starts at the part
            if (i > 0 && i < cells.size() - 1)
                points.add(faceCentre(c));
        }
        points.add(onFace(PartGeometry.anchor(to), to.face()));
        return points;
    }

    private static List<Cell> neighbours(BlockGetter level, Cell cell) {
        List<Cell> out = new ArrayList<>(4);
        for (Direction m : Direction.values()) {
            if (m.getAxis() == cell.face.getAxis())
                continue;
            BlockPos ahead = cell.pos.relative(m);
            if (!open(level, ahead))
                out.add(new Cell(cell.pos, m)); // a wall in front: climb it
            else if (solid(level, ahead.relative(cell.face)))
                out.add(new Cell(ahead, cell.face)); // flat
            else if (open(level, ahead.relative(cell.face)))
                out.add(new Cell(ahead.relative(cell.face), m.getOpposite())); // round the edge
        }
        return out;
    }

    /** The corner point between two cells whose faces meet at an edge. */
    private static Vec3 bend(Cell a, Cell b) {
        if (a.pos.equals(b.pos)) // inside corner: the edge between its two faces
            return Vec3.atCenterOf(a.pos).add(offset(a.face, 0.5 - LIFT)).add(offset(b.face, 0.5 - LIFT));
        // outside corner: round the edge of the block a lies on, towards b
        Direction along = b.face.getOpposite();
        return Vec3.atCenterOf(a.pos).add(offset(a.face, 0.5 - LIFT)).add(offset(along, 0.5 + LIFT));
    }

    private static Vec3 faceCentre(Cell c) {
        return Vec3.atCenterOf(c.pos).add(offset(c.face, 0.5 - LIFT));
    }

    /** A part's anchor brought down onto the face it's stuck to. */
    private static Vec3 onFace(Vec3 anchor, Direction face) {
        return anchor.add(offset(face, -(PartGeometry.HEIGHT / 16 / 2) + LIFT));
    }

    private static Vec3 offset(Direction d, double by) {
        return new Vec3(d.getStepX() * by, d.getStepY() * by, d.getStepZ() * by);
    }

    private static boolean open(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean solid(BlockGetter level, BlockPos pos) {
        return !open(level, pos);
    }

    public static int metres(List<Vec3> points, boolean aerial) {
        double total = 0;
        for (int i = 1; i < points.size(); i++)
            total += points.get(i).distanceTo(points.get(i - 1));
        // a hanging cable is a bit longer than the straight line, for its sag
        return (int) Math.ceil(aerial ? total * 1.05 : total);
    }

    private CableRoute() {}
}
