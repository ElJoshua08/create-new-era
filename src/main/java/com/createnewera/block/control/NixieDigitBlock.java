package com.createnewera.block.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.squiddev.cobalt.LuaError;

import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * A nixie tube on a panel: Create's own tube, glowing with any character a program gives it.
 * Tubes side by side, set the same way, make a row that reads as one display: a cable to any of
 * them, the first, the last or one in the middle, drives the whole row.
 */
public class NixieDigitBlock extends PanelControlBlock {
    /** Which way its character faces: towards whoever placed it, or out of the wall it's on */
    public static final DirectionProperty READS = DirectionProperty.create("reads", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public NixieDigitBlock(Properties properties, DirectionalShape shape) {
        super(properties.lightLevel(s -> s.getValue(LIT) ? 9 : 0), shape);
        registerDefaultState(defaultBlockState().setValue(READS, Direction.SOUTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(READS, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction facing = state.getValue(DirectionalPartBlock.FACING);
        return state.setValue(READS, facing.getAxis().isHorizontal() ? facing : context.getHorizontalDirection().getOpposite());
    }

    /**
     * The row this tube is in, left to right as read: its neighbours on either side, and theirs,
     * as long as they're nixies on the same face reading the same way.
     */
    public static List<BlockPos> row(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof NixieDigitBlock))
            return List.of(pos);
        Direction right = state.getValue(READS).getCounterClockWise();
        BlockPos start = pos;
        while (sameRow(state, level.getBlockState(start.relative(right.getOpposite()))) && start.distManhattan(pos) < MAX_ROW)
            start = start.relative(right.getOpposite());
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos at = start; out.size() < MAX_ROW && (at.equals(start) || sameRow(state, level.getBlockState(at))); at = at.relative(right))
            out.add(at);
        return out;
    }

    /** The longest row: a whole line of text, not a runaway search */
    public static final int MAX_ROW = 64;

    private static boolean sameRow(BlockState a, BlockState b) {
        return b.getBlock() instanceof NixieDigitBlock && b.getValue(FACING) == a.getValue(FACING) && b.getValue(READS) == a.getValue(READS);
    }

    private static PanelControlBlockEntity tube(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PanelControlBlockEntity tube ? tube : null;
    }

    /** Lights one tube with a character, "" for dark. */
    private static void show(Level level, BlockPos pos, String glyph, DyeColor color) {
        PanelControlBlockEntity tube = tube(level, pos);
        if (tube == null || tube.glyph.equals(glyph) && tube.color == color)
            return;
        tube.glyph = glyph;
        tube.color = color;
        tube.setChanged();
        BlockState state = level.getBlockState(pos);
        boolean lit = !glyph.isBlank();
        if (state.getValue(LIT) != lit)
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        else
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** Text across the row, one character a tube from the left; tubes past its end go dark. */
    private static void showText(Level level, List<BlockPos> row, String text) {
        int[] chars = text.codePoints().toArray();
        DyeColor color = color(level, row);
        for (int i = 0; i < row.size(); i++)
            show(level, row.get(i), i < chars.length ? Character.toString(chars[i]) : "", color);
    }

    private static DyeColor color(Level level, List<BlockPos> row) {
        PanelControlBlockEntity first = tube(level, row.get(0));
        return first == null ? DyeColor.ORANGE : first.color;
    }

    /** What the row shows, a space for each dark tube, without the dark ones at its end. */
    private static String text(Level level, List<BlockPos> row) {
        StringBuilder out = new StringBuilder();
        for (BlockPos at : row) {
            PanelControlBlockEntity tube = tube(level, at);
            out.append(tube == null || tube.glyph.isBlank() ? " " : tube.glyph);
        }
        return out.toString().stripTrailing();
    }

    /** A number as a program would print it, or with a set number of decimals. */
    static String number(double value, int decimals) {
        if (decimals >= 0)
            return String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (value == Math.rint(value) && Math.abs(value) < 1e15)
            return Long.toString((long) value);
        return Double.toString(value);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("getText", "", "string", args -> text(level, row(level, pos)));
        api.read("getLength", "", "number", args -> row(level, pos).size());
        api.read("getChar", "index: number", "string | nil", args -> {
            List<BlockPos> row = row(level, pos);
            int i = args.arg(1).checkInteger();
            if (i < 1 || i > row.size())
                return null;
            PanelControlBlockEntity tube = tube(level, row.get(i - 1));
            return tube == null || tube.glyph.isBlank() ? null : tube.glyph;
        });
        api.read("getColor", "", "string", args -> color(level, row(level, pos)).getSerializedName());
        api.order("setText", "text: string", "", args -> {
            showText(level, row(level, pos), args.arg(1).checkString());
            return null;
        });
        api.order("show", "number: number, decimals?: number", "", args -> {
            List<BlockPos> row = row(level, pos);
            String shown = number(args.arg(1).checkDouble(), args.arg(2).isNil() ? -1 : args.arg(2).checkInteger());
            // too long for the row: dashes, as a meter shows it's off the scale
            if (shown.length() > row.size())
                shown = "-".repeat(row.size());
            showText(level, row, " ".repeat(row.size() - shown.length()) + shown);
            return null;
        });
        api.order("setChar", "index: number, char: string | nil", "", args -> {
            List<BlockPos> row = row(level, pos);
            int i = args.arg(1).checkInteger();
            if (i < 1 || i > row.size())
                throw new LuaError("bad argument #1 to 'setChar' (index 1-" + row.size() + " expected, got " + i + ")");
            String c = args.arg(2).isNil() ? "" : args.arg(2).checkString();
            int[] chars = c.codePoints().toArray();
            show(level, row.get(i - 1), chars.length == 0 ? "" : Character.toString(chars[0]), color(level, row));
            return null;
        });
        api.order("clear", "", "", args -> {
            showText(level, row(level, pos), "");
            return null;
        });
        api.order("setColor", "color: name", "", args -> {
            String name = args.arg(1).checkString();
            DyeColor color = DyeColor.byName(name, null);
            if (color == null)
                throw new LuaError("bad argument #1 to 'setColor' (a dye colour expected, got \"" + name + "\")");
            for (BlockPos at : row(level, pos)) {
                PanelControlBlockEntity tube = tube(level, at);
                if (tube != null)
                    show(level, at, tube.glyph, color);
            }
            return null;
        });
        api.choices("setColor", Arrays.stream(DyeColor.values()).map(DyeColor::getSerializedName).toList());
    }

    @Override
    public Object value(BlockState state) {
        return "";
    }

    @Override
    public Object value(Level level, BlockPos pos) {
        return text(level, row(level, pos));
    }

    @Override
    public Object snapshot(Level level, BlockPos pos) {
        List<BlockPos> row = row(level, pos);
        return List.of(text(level, row), color(level, row), row.size());
    }

    @Override
    public String valueType() {
        return "string";
    }

    @Override
    public Component goggleValue(BlockState state) {
        return state.getValue(LIT) ? Component.translatable("create_new_era.goggles.digit", "…") : Component.translatable("create_new_era.goggles.dark");
    }

    @Override
    public Component goggleValue(Level level, BlockPos pos) {
        String text = text(level, row(level, pos));
        return text.isBlank() ? Component.translatable("create_new_era.goggles.dark")
                : Component.translatable("create_new_era.goggles.digit", text);
    }
}
