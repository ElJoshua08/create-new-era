package com.createnewera.gametest;

import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.registry.ModBlocks;
import com.createnewera.registry.ModCreativeTabs;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
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
            "nixie_digit", "panel_gauge", "panel_keypad");

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
        if (count != 13)
            helper.fail("main tab has " + count + " items, expected 13");
        helper.succeed();
    }
}
