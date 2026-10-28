package com.createnewera.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shape authored pointing up (like the block models) and rotated to all six facings
 * the same way blockstate x/y rotations turn the model.
 */
public final class DirectionalShape {
    private final Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);

    private DirectionalShape(double[][] boxes) {
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes)
                shape = Shapes.or(shape, rotated(b, facing));
            byFacing.put(facing, shape.optimize());
        }
    }

    /** Boxes as {minX, minY, minZ, maxX, maxY, maxZ} in pixels, for the block facing up. */
    public static DirectionalShape up(double[]... boxes) {
        return new DirectionalShape(boxes);
    }

    public VoxelShape get(Direction facing) {
        return byFacing.get(facing);
    }

    private static VoxelShape rotated(double[] b, Direction facing) {
        double[] p = map(b[0], b[1], b[2], facing);
        double[] q = map(b[3], b[4], b[5], facing);
        return Block.box(Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.min(p[2], q[2]),
                Math.max(p[0], q[0]), Math.max(p[1], q[1]), Math.max(p[2], q[2]));
    }

    /** A point (in pixels) of something authored facing up, where it ends up facing {@code facing}. */
    public static double[] map(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case UP -> new double[] {x, y, z};
            case DOWN -> new double[] {x, 16 - y, 16 - z};
            case NORTH -> new double[] {x, z, 16 - y};
            case SOUTH -> new double[] {16 - x, z, y};
            case EAST -> new double[] {y, z, x};
            case WEST -> new double[] {16 - y, z, 16 - x};
        };
    }
}
