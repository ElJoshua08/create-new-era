package com.createnewera.bus;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.DirectionalShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where parts sit in the world. A part is authored like the panel controls, pointing up with
 * its base on the face below it, and turned to each face the way DirectionalShape turns them:
 * it lives in the space just outside the face, in the neighbouring block.
 */
public final class PartGeometry {
    /** How far a part stands off the face, in pixels. */
    public static final double HEIGHT = 1.5;

    /** The part's box in the world, for outlines and clicks. */
    public static AABB box(BusPort part) {
        return box(part.pos(), part.face(), part.u(), part.v(), part.kind().size);
    }

    public static AABB box(BlockPos pos, Direction face, int u, int v, int size) {
        BlockPos outside = pos.relative(face);
        double[] a = DirectionalShape.map(u, 0, v, face);
        double[] b = DirectionalShape.map(u + size, HEIGHT, v + size, face);
        return new AABB(
                outside.getX() + Math.min(a[0], b[0]) / 16, outside.getY() + Math.min(a[1], b[1]) / 16, outside.getZ() + Math.min(a[2], b[2]) / 16,
                outside.getX() + Math.max(a[0], b[0]) / 16, outside.getY() + Math.max(a[1], b[1]) / 16, outside.getZ() + Math.max(a[2], b[2]) / 16);
    }

    /** Where a cable plugs in: the middle of the part, or for a panel control the middle of its plate's near edge. */
    public static Vec3 anchor(BusPort part) {
        return part.kind() == BusPort.Kind.CONTROL ? plateEdge(part, 8, 14) : box(part).getCenter();
    }

    /**
     * Where a cable heading for {@code toward} plugs in. A panel control's connection runs all
     * round the edge of its plate, so the cable leaves from the point of that edge nearest where
     * it's going; anything else has the one spot.
     */
    public static Vec3 anchor(BusPort part, Vec3 toward) {
        if (part.kind() != BusPort.Kind.CONTROL)
            return anchor(part);
        double[] t = DirectionalShape.unmap((toward.x - part.pos().getX()) * 16, (toward.y - part.pos().getY()) * 16,
                (toward.z - part.pos().getZ()) * 16, part.face());
        double x = Mth.clamp(t[0], PLATE_FROM, PLATE_TO), z = Mth.clamp(t[2], PLATE_FROM, PLATE_TO);
        // inside the plate's outline (straight above it, say): out to the nearest side
        double toLeft = x - PLATE_FROM, toRight = PLATE_TO - x, toNear = z - PLATE_FROM, toFar = PLATE_TO - z;
        double nearest = Math.min(Math.min(toLeft, toRight), Math.min(toNear, toFar));
        if (nearest > 0) {
            if (nearest == toLeft) x = PLATE_FROM;
            else if (nearest == toRight) x = PLATE_TO;
            else if (nearest == toNear) z = PLATE_FROM;
            else z = PLATE_TO;
        }
        return plateEdge(part, x, z);
    }

    /** A panel control's andesite plate, in the up-facing model's pixels. */
    private static final double PLATE_FROM = 2, PLATE_TO = 14;

    private static Vec3 plateEdge(BusPort part, double x, double z) {
        double[] at = DirectionalShape.map(x, 1, z, part.face());
        return new Vec3(part.pos().getX() + at[0] / 16, part.pos().getY() + at[1] / 16, part.pos().getZ() + at[2] / 16);
    }

    /**
     * The grid spot a part of {@code size} gets when placed where the player clicked: centred
     * on the click, pushed back inside the face if it would hang off an edge.
     *
     * @return {u, v}
     */
    public static int[] spotAt(BlockPos pos, Direction face, Vec3 hit, int size) {
        // the click in pixels, local to the block space just outside the face
        BlockPos outside = pos.relative(face);
        double x = (hit.x - outside.getX()) * 16, y = (hit.y - outside.getY()) * 16, z = (hit.z - outside.getZ()) * 16;
        // undo DirectionalShape.map for a point on the face (height 0)
        double[] up = switch (face) {
            case UP -> new double[] {x, z};
            case DOWN -> new double[] {x, 16 - z};
            case NORTH -> new double[] {x, y};
            case SOUTH -> new double[] {16 - x, y};
            case EAST -> new double[] {z, y};
            case WEST -> new double[] {16 - z, y};
        };
        int u = Mth.clamp((int) Math.floor(up[0] - size / 2.0 + 0.5), 0, 16 - size);
        int v = Mth.clamp((int) Math.floor(up[1] - size / 2.0 + 0.5), 0, 16 - size);
        return new int[] {u, v};
    }

    /** The part on this face that the click landed on, if any. */
    public static @Nullable BusPort partAt(Iterable<BusPort> parts, Direction face, Vec3 hit) {
        for (BusPort part : parts)
            // a control's connection is the whole control: clicking it works the control
            if (part.kind() != BusPort.Kind.CONTROL && part.face() == face && box(part).inflate(0.01).contains(hit))
                return part;
        return null;
    }

    private PartGeometry() {}
}
