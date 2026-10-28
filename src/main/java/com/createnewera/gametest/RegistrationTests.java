package com.createnewera.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.block.BrassScreenBlock;
import com.createnewera.registry.ModBlocks;
import com.createnewera.registry.ModCreativeTabs;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run with ./gradlew runGameTestServer: every block places in every facing and every recipe parses. */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class RegistrationTests {
    // the holder namespace is prepended, so this resolves to create_new_era:empty3
    private static final String EMPTY = "empty3";

    private static final List<String> RECIPES = List.of(
            "brass_computer", "brass_screen", "universal_adapter", "bearing_servo", "control_plate",
            "panel_button", "panel_switch", "panel_knob", "panel_slider", "panel_lamp",
            "nixie_digit", "panel_gauge", "panel_keypad", "bus_port", "port_expansion");

    @GameTest(template = EMPTY)
    public static void everyBlockPlacesInEveryFacing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        List<BlockEntry<? extends Block>> blocks = List.of(
                ModBlocks.BRASS_COMPUTER, ModBlocks.BRASS_SCREEN, ModBlocks.UNIVERSAL_ADAPTER, ModBlocks.BEARING_SERVO,
                ModBlocks.PANEL_BUTTON, ModBlocks.PANEL_SWITCH, ModBlocks.PANEL_KNOB, ModBlocks.PANEL_SLIDER,
                ModBlocks.PANEL_LAMP, ModBlocks.NIXIE_DIGIT, ModBlocks.PANEL_GAUGE, ModBlocks.PANEL_KEYPAD);
        for (BlockEntry<? extends Block> entry : blocks) {
            BlockState base = entry.getDefaultState();
            for (Direction facing : Direction.values()) {
                BlockState state;
                if (base.hasProperty(BlockStateProperties.FACING))
                    state = base.setValue(BlockStateProperties.FACING, facing);
                else if (facing.getAxis().isHorizontal())
                    state = base.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                else
                    continue;
                helper.setBlock(pos, state);
                helper.assertBlockState(pos, s -> s == state, () -> entry.getId() + " did not keep " + state);
                if (helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos)).isEmpty())
                    helper.fail(entry.getId() + " has an empty shape facing " + facing);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyRecipeLoads(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String name : RECIPES) {
            if (recipes.byKey(CreateNewEra.asResource(name)).isEmpty())
                helper.fail("recipe not loaded: " + name);
        }
        helper.succeed();
    }

    // building the tabs is what crashed the client when Registrate also put every item in the search tab
    @GameTest(template = EMPTY)
    public static void creativeTabsBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        int count = ModCreativeTabs.MAIN.get().getDisplayItems().size();
        if (count != 15)
            helper.fail("main tab has " + count + " items, expected 15");
        helper.succeed();
    }

    // a 2x2 wall facing north joins into one pane; a screen facing elsewhere stays apart
    @GameTest(template = EMPTY)
    public static void screensJoin(GameTestHelper helper) {
        BlockState north = ModBlocks.BRASS_SCREEN.getDefaultState().setValue(BrassScreenBlock.FACING, Direction.NORTH);
        BlockPos[] wall = {new BlockPos(0, 1, 1), new BlockPos(1, 1, 1), new BlockPos(0, 2, 1), new BlockPos(1, 2, 1)};
        for (BlockPos pos : wall)
            placeLikeAPlayer(helper, pos, north);
        placeLikeAPlayer(helper, new BlockPos(2, 1, 1), north.setValue(BrassScreenBlock.FACING, Direction.EAST));

        // facing north, the viewer's left is east (+x)
        expectJoins(helper, wall[0], true, false, true, false);
        expectJoins(helper, wall[1], true, false, false, true);
        expectJoins(helper, wall[2], false, true, true, false);
        expectJoins(helper, wall[3], false, true, false, true);
        expectJoins(helper, new BlockPos(2, 1, 1), false, false, false, false);

        helper.destroyBlock(wall[1]);
        expectJoins(helper, wall[0], true, false, false, false);
        helper.succeed();
    }

    private static void placeLikeAPlayer(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, Block.updateFromNeighbourShapes(state, helper.getLevel(), helper.absolutePos(pos)));
    }

    private static void expectJoins(GameTestHelper helper, BlockPos pos, boolean up, boolean down, boolean left, boolean right) {
        BlockState s = helper.getBlockState(pos);
        boolean[] actual = {s.getValue(BrassScreenBlock.UP), s.getValue(BrassScreenBlock.DOWN),
                s.getValue(BrassScreenBlock.LEFT), s.getValue(BrassScreenBlock.RIGHT)};
        boolean[] expected = {up, down, left, right};
        if (!Arrays.equals(actual, expected))
            helper.fail("screen at " + pos + " joins " + Arrays.toString(actual)
                    + ", expected " + Arrays.toString(expected));
    }

    // every block in every Ponder structure must resolve to the state we wrote, or the scene shows the wrong thing
    @GameTest(template = EMPTY)
    public static void ponderStructuresLoad(GameTestHelper helper) {
        var blocks = helper.getLevel().holderLookup(Registries.BLOCK);
        for (String name : List.of("computer/intro", "screen/join", "adapter/attach", "servo/intro", "controls/desk")) {
            String path = "/assets/create_new_era/ponder/" + name + ".nbt";
            try (InputStream in = RegistrationTests.class.getResourceAsStream(path)) {
                if (in == null) {
                    helper.fail("missing " + path);
                    return;
                }
                CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) {
                    CompoundTag written = (CompoundTag) entry;
                    BlockState state = NbtUtils.readBlockState(blocks, written);
                    if (state.isAir())
                        helper.fail(name + ": unknown block " + written.getString("Name"));
                    // readBlockState silently drops properties it doesn't know
                    CompoundTag properties = written.getCompound("Properties");
                    for (String key : properties.getAllKeys())
                        if (state.getProperties().stream().noneMatch(p -> p.getName().equals(key)))
                            helper.fail(name + ": " + written.getString("Name") + " has no property " + key);
                }
            } catch (IOException e) {
                helper.fail(name + ": " + e);
            }
        }
        helper.succeed();
    }
}
