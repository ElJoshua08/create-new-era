package com.createnewera.block.screen;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import com.createnewera.block.BrassScreenBlock;
import com.createnewera.block.HorizontalMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pane a screen is part of: the screens around it facing the same way, joined into one
 * rectangle, as seen from the front. Its pixels belong to its top-left block (the controller):
 * four to each of Minecraft's pixels, across the glass inside the outer brass frame.
 *
 * @param origin  the top-left block, as seen from the front
 * @param columns blocks across
 * @param rows    blocks down
 */
public record ScreenPane(BlockPos origin, Direction facing, int columns, int rows) {
    /** Screen pixels to one of Minecraft's: a block's pixel is four by four of them */
    public static final int DENSITY = 4;
    /** The brass frame round the pane's edge, in Minecraft's pixels */
    public static final int FRAME = 2;
    /** The biggest pane, in blocks each way: past it the screens split into panes of their own */
    public static final int MAX = 8;

    /** The screens joined with this one, or null if it isn't a screen. */
    public static ScreenPane of(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BrassScreenBlock))
            return null;
        Direction facing = state.getValue(HorizontalMachineBlock.FACING);
        Direction right = right(facing);
        // every screen reachable through joined sides, no further than a pane can reach
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        int minCol = 0, maxCol = 0, minRow = 0, maxRow = 0;
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int col = along(at.subtract(pos), right), row = -at.getY() + pos.getY();
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
            for (Direction side : new Direction[] {Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
                BlockPos next = at.relative(side);
                int nc = along(next.subtract(pos), right), nr = -next.getY() + pos.getY();
                if (Math.max(maxCol, nc) - Math.min(minCol, nc) >= MAX || Math.max(maxRow, nr) - Math.min(minRow, nr) >= MAX)
                    continue;
                BlockState other = level.getBlockState(next);
                if (!seen.contains(next) && other.getBlock() instanceof BrassScreenBlock
                        && other.getValue(HorizontalMachineBlock.FACING) == facing) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        BlockPos origin = pos.relative(right, minCol).relative(Direction.DOWN, minRow);
        return new ScreenPane(origin.immutable(), facing, maxCol - minCol + 1, maxRow - minRow + 1);
    }

    /** The viewer's right, looking at the front. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static int along(BlockPos offset, Direction axis) {
        return offset.getX() * axis.getStepX() + offset.getZ() * axis.getStepZ();
    }

    /** Pixels across: the glass between the outer frame. */
    public int width() {
        return (columns * 16 - 2 * FRAME) * DENSITY;
    }

    public int height() {
        return (rows * 16 - 2 * FRAME) * DENSITY;
    }

    /** Which block of the pane this is: {column from the left, row from the top}. */
    public int[] cell(BlockPos pos) {
        return new int[] {along(pos.subtract(origin), right(facing)), origin.getY() - pos.getY()};
    }

    public boolean contains(BlockPos pos) {
        int[] c = cell(pos);
        return c[0] >= 0 && c[0] < columns && c[1] >= 0 && c[1] < rows
                && pos.subtract(origin).get(facing.getAxis()) == 0;
    }
}
