package com.createnewera.bus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.control.PanelControlBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
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
        // each end faces the other: a control's cable leaves its plate on the side it's heading for
        Vec3 a = PartGeometry.anchor(from, PartGeometry.anchor(to));
        Vec3 b = PartGeometry.anchor(to, a);
        a = PartGeometry.anchor(from, b);
        return a.distanceTo(b) <= MAX_AERIAL ? List.of(a, b) : null;
    }

    private record Cell(BlockPos pos, Direction face) {}

    /**
     * The shortest run along faces from one part to the other, or null when the faces they sit
     * on aren't joined by open surface (or it's too long).
     */
    public static @Nullable List<Vec3> surface(BlockGetter level, BusPort from, BusPort to) {
        Cell start = cellOf(from), goal = cellOf(to);
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
        Vec3 a = PartGeometry.anchor(from, PartGeometry.anchor(to)), b = PartGeometry.anchor(to, a);
        return pull(level, cells, onFace(a, from.face()), onFace(b, to.face()));
    }

    /**
     * A point the cable passes through: fixed (its ends), the middle of a face it may skip
     * (dropped wherever a straight line along that face does the job), or a bend that slides
     * along the edge it sits on.
     *
     * @param face the face the cable lies on from this point to the next
     * @param axis for a bend, the edge's direction; null otherwise
     */
    private static final class Node {
        Vec3 at;
        final Direction face;
        final boolean optional;
        final Vec3 edgeMiddle, axis;

        Node(Vec3 at, Direction face, boolean optional, Vec3 edgeMiddle, Vec3 axis) {
            this.at = at;
            this.face = face;
            this.optional = optional;
            this.edgeMiddle = edgeMiddle;
            this.axis = axis;
        }
    }

    /** How far a bend may slide from the middle of its edge: not right into the corner. */
    private static final double BEND_REACH = 0.5 - 0.125;

    /**
     * Pulls the cable tight along the cells, like a string: straight across each face wherever
     * the face allows it, bending where its straightened line crosses each edge.
     */
    private static List<Vec3> pull(BlockGetter level, List<Cell> cells, Vec3 start, Vec3 end) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(start, cells.get(0).face, false, null, null));
        for (int i = 1; i < cells.size(); i++) {
            Cell prev = cells.get(i - 1), c = cells.get(i);
            if (prev.face != c.face) {
                Vec3 middle = bend(prev, c);
                Direction along = prev.pos.equals(c.pos) ? c.face : c.face.getOpposite();
                Vec3 axis = offset(prev.face, 1).cross(offset(along, 1));
                nodes.add(new Node(middle, c.face, false, middle, axis));
            }
            if (i < cells.size() - 1)
                nodes.add(new Node(faceCentre(c), c.face, true, null, null));
        }
        nodes.add(new Node(end, cells.get(cells.size() - 1).face, false, null, null));
        List<Vec3> unpulled = new ArrayList<>(nodes.size());
        for (Node n : nodes)
            unpulled.add(n.at);

        for (int round = 0; round < 6; round++) {
            slideBends(nodes);
            // drop face middles a straight line can do without, nearest the ends first
            for (int i = 1; i < nodes.size() - 1; i++) {
                Node n = nodes.get(i);
                if (n.optional && n.face == nodes.get(i - 1).face
                        && alongFace(level, nodes.get(i - 1).at, nodes.get(i + 1).at, n.face)) {
                    nodes.remove(i);
                    i--;
                }
            }
        }
        slideBends(nodes);
        // a bend that slid can pull a run across something on the face: then keep the safe route
        for (int i = 0; i < nodes.size() - 1; i++)
            if (!alongFace(level, nodes.get(i).at, nodes.get(i + 1).at, nodes.get(i).face))
                return unpulled;
        List<Vec3> points = new ArrayList<>(nodes.size());
        for (Node n : nodes)
            points.add(n.at);
        return points;
    }

    /** Each bend moves along its edge to where the cable through it is shortest. */
    private static void slideBends(List<Node> nodes) {
        for (int pass = 0; pass < 4; pass++)
            for (int i = 1; i < nodes.size() - 1; i++) {
                Node n = nodes.get(i);
                if (n.axis == null)
                    continue;
                Vec3 before = nodes.get(i - 1).at, after = nodes.get(i + 1).at;
                // the length through the bend is convex in its position along the edge
                double lo = -BEND_REACH, hi = BEND_REACH;
                for (int k = 0; k < 40; k++) {
                    double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
                    if (through(before, n, m1, after) < through(before, n, m2, after))
                        hi = m2;
                    else
                        lo = m1;
                }
                n.at = n.edgeMiddle.add(n.axis.scale((lo + hi) / 2));
            }
    }

    private static double through(Vec3 before, Node bend, double t, Vec3 after) {
        Vec3 at = bend.edgeMiddle.add(bend.axis.scale(t));
        return before.distanceTo(at) + at.distanceTo(after);
    }

    /**
     * Whether a straight run from a to b stays on open face: every block it passes over is open
     * with something solid under it on that side. The very ends are left out, as they sit on the
     * edges where the cable bends.
     */
    private static boolean alongFace(BlockGetter level, Vec3 a, Vec3 b, Direction face) {
        double length = a.distanceTo(b);
        int steps = Math.max(2, (int) Math.ceil(length * 8));
        for (int i = 0; i <= steps; i++) {
            double along = length * i / steps;
            if (along < 0.15 || along > length - 0.15)
                continue;
            BlockPos cell = BlockPos.containing(a.lerp(b, (double) i / steps));
            if (!open(level, cell) || !solid(level, cell.relative(face)))
                return false;
        }
        return true;
    }

    /** The cell a part's cable starts from: the space in front of it, or a control's own space. */
    private static Cell cellOf(BusPort part) {
        if (part.kind() == BusPort.Kind.CONTROL) // lying on the block that holds the control
            return new Cell(part.pos(), part.face().getOpposite());
        return new Cell(part.pos().relative(part.face()), part.face().getOpposite());
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

    /** Room for a cable: nothing there, or only a thin panel control it can run past. */
    private static boolean open(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PanelControlBlock || state.getCollisionShape(level, pos).isEmpty();
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
