package com.createnewera.bus;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Where a block really is when Aeronautics has made it part of a ship: Sable keeps a ship's
 * blocks far away in a plot of their own and moves a copy through the world, so a block's
 * position says nothing of the ship's height, heading or speed. Sable's companion API answers
 * that; it's reached by reflection, so without Sable every block is simply on solid ground.
 */
public final class Ships {
    private static boolean looked;
    private static Object companion;
    private static Method containing, containingClient, project, velocity, airVelocity, logicalPose, renderPose, orientation,
            transformPosition, transformPositionInverse;

    /** The centre of the block, in the world, wherever its ship has taken it. */
    public static Vec3 position(Level level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (!ready())
            return center;
        try {
            return (Vec3) project.invoke(companion, level, center);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return center;
        }
    }

    /**
     * How the block's ship is turned in the world; no turn off a ship. On the client, the
     * pose being drawn this frame, so instruments move as smoothly as the ship.
     */
    public static Quaterniondc orientation(Level level, BlockPos pos, float partialTick) {
        if (!ready())
            return new Quaterniond();
        try {
            Object ship, pose;
            if (level.isClientSide) {
                ship = containingClient.invoke(companion, Vec3.atCenterOf(pos));
                pose = ship == null ? null : renderPose.invoke(ship, partialTick);
            } else {
                ship = containing.invoke(companion, level, Vec3.atCenterOf(pos));
                pose = ship == null ? null : logicalPose.invoke(ship);
            }
            return pose == null ? new Quaterniond() : new Quaterniond((Quaterniondc) orientation.invoke(pose));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Quaterniond();
        }
    }

    /**
     * Client: how the ship holding this point (in its plot) is drawn this frame, to draw what we
     * keep in the plot (ports, cables) where the ship shows; null off a ship.
     */
    public static @Nullable DrawnShip drawn(Vec3 point, float partialTick) {
        if (!ready())
            return null;
        try {
            Object ship = containingClient.invoke(companion, point);
            if (ship == null)
                return null;
            Object pose = renderPose.invoke(ship, partialTick);
            return new DrawnShip(pose, new Quaterniond((Quaterniondc) orientation.invoke(pose)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** A ship as drawn this frame: where a point of its plot shows in the world, and how it's turned. */
    public record DrawnShip(Object pose, Quaterniondc orientation) {
        public Vec3 toPlot(Vec3 world) {
            try {
                return (Vec3) transformPositionInverse.invoke(pose, world);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return world;
            }
        }

        public Vec3 toWorld(Vec3 plot) {
            try {
                return (Vec3) transformPosition.invoke(pose, plot);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return plot;
            }
        }
    }

    /** How fast the block moves through the world, in blocks a second; through the air if {@code air}. */
    public static Vec3 velocity(Level level, BlockPos pos, boolean air) {
        if (!ready())
            return Vec3.ZERO;
        try {
            Vec3 v = (Vec3) (air ? airVelocity : velocity).invoke(companion, level, Vec3.atCenterOf(pos));
            return v == null ? Vec3.ZERO : v;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Vec3.ZERO;
        }
    }

    /**
     * An instrument's reading of its ship's attitude: the ship flies the way the instrument's
     * reader faces ({@code forward}), its up is the plot's up.
     *
     * @return {pitch, roll, heading} in degrees: nose up, right wing down, and 0 north, 90 east
     */
    public static double[] attitude(Quaterniondc ship, Direction forward) {
        Vector3d fwd = ship.transform(new Vector3d(forward.getStepX(), 0, forward.getStepZ()));
        Vector3d up = ship.transform(new Vector3d(0, 1, 0));
        Vector3d right = new Vector3d(forward.getStepX(), 0, forward.getStepZ()).cross(0, 1, 0);
        ship.transform(right);
        double pitch = Math.toDegrees(Math.asin(Math.clamp(fwd.y, -1, 1)));
        double roll = Math.toDegrees(Math.atan2(-right.y, up.y));
        double heading = (Math.toDegrees(Math.atan2(fwd.x, -fwd.z)) + 360) % 360;
        return new double[] {pitch, roll, heading};
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            if (!ModList.get().isLoaded("sable"))
                return false;
            try {
                Class<?> api = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                companion = api.getField("INSTANCE").get(null);
                containing = api.getMethod("getContaining", Level.class, net.minecraft.core.Position.class);
                containingClient = api.getMethod("getContainingClient", net.minecraft.core.Position.class);
                project = api.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
                velocity = api.getMethod("getVelocity", Level.class, Vec3.class);
                airVelocity = api.getMethod("getVelocityRelativeToAir", Level.class, Vec3.class);
                logicalPose = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess").getMethod("logicalPose");
                renderPose = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess").getMethod("renderPose", float.class);
                orientation = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("orientation");
                transformPosition = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformPosition", Vec3.class);
                transformPositionInverse = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformPositionInverse", Vec3.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                companion = null;
            }
        }
        return companion != null;
    }

    private Ships() {}
}
