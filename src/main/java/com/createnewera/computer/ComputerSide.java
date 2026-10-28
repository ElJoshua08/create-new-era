package com.createnewera.computer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

/** Sides as a program names them, relative to the computer's front. Left and right are as seen facing the front. */
public enum ComputerSide {
    FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM;

    public String luaName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static @Nullable ComputerSide byLuaName(String name) {
        for (ComputerSide side : values())
            if (side.luaName().equals(name))
                return side;
        return null;
    }

    public Direction toDirection(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    public static ComputerSide fromDirection(Direction facing, Direction direction) {
        for (ComputerSide side : values())
            if (side.toDirection(facing) == direction)
                return side;
        throw new IllegalArgumentException(direction.toString());
    }
}
