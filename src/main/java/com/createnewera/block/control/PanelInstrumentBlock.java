package com.createnewera.block.control;

import java.util.List;
import java.util.Map;

import org.squiddev.cobalt.LuaError;

import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;
import com.createnewera.bus.Ships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A flight instrument on a panel: it reads the ship it's built into (or the ground it stands
 * on) and shows it on a dial, and programs read the same numbers. Its 12 o'clock points the way
 * the ship flies: away from whoever placed it on a desk, into the wall it hangs on.
 */
public class PanelInstrumentBlock extends PanelControlBlock {
    public enum Kind {
        /** height in the world: a long needle a hundred blocks a turn, a short one a thousand */
        ALTIMETER,
        /** an artificial horizon: pitch and roll */
        GYROSCOPE,
        /** a card that turns to the heading */
        COMPASS,
        /** speed through the world, over three quarters of a turn up to its top speed */
        AIRSPEED
    }

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty READS = NixieDigitBlock.READS;
    /** The airspeed dial's top speed until a program sets another, in blocks a second */
    public static final double TOP_SPEED = 40;

    private final Kind kind;

    public PanelInstrumentBlock(Properties properties, DirectionalShape shape, Kind kind) {
        super(properties, shape);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(READS, Direction.SOUTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(READS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction facing = state.getValue(DirectionalPartBlock.FACING);
        return state.setValue(READS, facing.getAxis().isHorizontal() ? facing : context.getHorizontalDirection().getOpposite());
    }

    /** The way its ship flies, in the ship's own blocks: the way its reader faces. */
    public static Direction forward(BlockState state) {
        return state.getValue(READS).getOpposite();
    }

    /** {pitch, roll, heading} of the ship, in degrees. */
    public static double[] attitude(Level level, BlockPos pos, float partialTick) {
        return Ships.attitude(Ships.orientation(level, pos, partialTick), forward(level.getBlockState(pos)));
    }

    private static double round(double v, int places) {
        double scale = Math.pow(10, places);
        return Math.round(v * scale) / scale;
    }

    private static PanelControlBlockEntity entity(Level level, BlockPos pos) throws LuaError {
        if (level.getBlockEntity(pos) instanceof PanelControlBlockEntity be)
            return be;
        throw new LuaError("the instrument is gone");
    }

    /** The eight points of the compass, from a heading. */
    static String point(double heading) {
        String[] points = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        return points[(int) Math.round(heading / 45) % 8];
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        switch (kind) {
            case ALTIMETER -> {
                api.read("getAltitude", "", "blocks", args -> round(Ships.position(level, pos).y, 2));
                api.read("getHeightAboveGround", "", "blocks", args -> {
                    Vec3 at = Ships.position(level, pos);
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
                    return round(at.y - ground, 2);
                });
                api.read("getVerticalSpeed", "", "blocks/s", args -> round(Ships.velocity(level, pos, false).y, 2));
            }
            case GYROSCOPE -> {
                api.read("getPitch", "", "degrees", args -> round(attitude(level, pos, 1)[0], 2));
                api.read("getRoll", "", "degrees", args -> round(attitude(level, pos, 1)[1], 2));
                api.read("getHeading", "", "degrees 0-360", args -> round(attitude(level, pos, 1)[2], 2));
                api.read("getAttitude", "", "{pitch, roll, heading}", args -> {
                    double[] a = attitude(level, pos, 1);
                    return Map.of("pitch", round(a[0], 2), "roll", round(a[1], 2), "heading", round(a[2], 2));
                });
            }
            case COMPASS -> {
                api.read("getHeading", "", "degrees 0-360", args -> round(attitude(level, pos, 1)[2], 2));
                api.read("getDirection", "", "\"north\", \"north_east\"...", args -> point(attitude(level, pos, 1)[2]));
            }
            case AIRSPEED -> {
                api.read("getSpeed", "", "blocks/s", args -> round(Ships.velocity(level, pos, false).length(), 2));
                api.read("getAirspeed", "", "blocks/s", args -> round(Ships.velocity(level, pos, true).length(), 2));
                api.read("getVelocity", "", "{x, y, z}", args -> {
                    Vec3 v = Ships.velocity(level, pos, false);
                    return Map.of("x", round(v.x, 2), "y", round(v.y, 2), "z", round(v.z, 2));
                });
                api.read("getMaxSpeed", "", "blocks/s", args -> entity(level, pos).max);
                api.order("setMaxSpeed", "speed: blocks/s", "", args -> {
                    double top = args.arg(1).checkDouble();
                    if (!(top > 0))
                        throw new LuaError("bad argument #1 to 'setMaxSpeed' (a speed above 0 expected)");
                    PanelControlBlockEntity be = entity(level, pos);
                    be.max = top;
                    be.setChanged();
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
                    return null;
                });
            }
        }
    }

    /** What read() and onChange give: the instrument's whole reading, rounded so onChange isn't woken by jitter. */
    @Override
    public Object value(Level level, BlockPos pos) {
        return switch (kind) {
            case ALTIMETER -> round(Ships.position(level, pos).y, 1);
            case GYROSCOPE -> {
                double[] a = attitude(level, pos, 1);
                yield Map.of("pitch", round(a[0], 1), "roll", round(a[1], 1), "heading", round(a[2], 1));
            }
            case COMPASS -> round(attitude(level, pos, 1)[2], 1);
            case AIRSPEED -> round(Ships.velocity(level, pos, false).length(), 1);
        };
    }

    @Override
    public Object snapshot(Level level, BlockPos pos) {
        return List.of(value(level, pos));
    }

    @Override
    public Object value(BlockState state) {
        return 0;
    }

    @Override
    public String valueType() {
        return switch (kind) {
            case ALTIMETER -> "blocks";
            case GYROSCOPE -> "{pitch, roll, heading}";
            case COMPASS -> "degrees 0-360";
            case AIRSPEED -> "blocks/s";
        };
    }

    @Override
    public Component goggleValue(BlockState state) {
        return Component.empty();
    }

    @Override
    public Component goggleValue(Level level, BlockPos pos) {
        return switch (kind) {
            case ALTIMETER -> Component.translatable("create_new_era.goggles.altitude", String.format(java.util.Locale.ROOT, "%.1f", Ships.position(level, pos).y));
            case GYROSCOPE -> {
                double[] a = attitude(level, pos, 1);
                yield Component.translatable("create_new_era.goggles.attitude", String.format(java.util.Locale.ROOT, "%.1f", a[0]),
                        String.format(java.util.Locale.ROOT, "%.1f", a[1]));
            }
            case COMPASS -> Component.translatable("create_new_era.goggles.heading", Math.round(attitude(level, pos, 1)[2]));
            case AIRSPEED -> Component.translatable("create_new_era.goggles.speed",
                    String.format(java.util.Locale.ROOT, "%.1f", level.getBlockEntity(pos) instanceof PanelControlBlockEntity be ? be.shownSpeed : 0));
        };
    }
}
