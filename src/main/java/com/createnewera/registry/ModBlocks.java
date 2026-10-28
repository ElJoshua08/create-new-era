package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.DirectionalPartBlock.Placement;
import com.createnewera.block.DirectionalShape;
import com.createnewera.block.HorizontalMachineBlock;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public final class ModBlocks {
    static {
        REGISTRATE.setCreativeTab(ModCreativeTabs.MAIN);
    }

    public static final BlockEntry<HorizontalMachineBlock> BRASS_COMPUTER = REGISTRATE
            .block("brass_computer", HorizontalMachineBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops())
            .transform(TagGen.pickaxeOnly())
            .blockstate((c, p) -> p.horizontalBlock(c.get(), existing(p, c.getName())))
            .lang("Brass Computer")
            .item().model(ModBlocks::blockItemModel).build()
            .register();

    public static final BlockEntry<HorizontalMachineBlock> BRASS_SCREEN = REGISTRATE
            .block("brass_screen", HorizontalMachineBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops())
            .transform(TagGen.pickaxeOnly())
            .blockstate((c, p) -> p.horizontalBlock(c.get(), existing(p, c.getName())))
            .lang("Brass Screen")
            .item().model(ModBlocks::blockItemModel).build()
            .register();

    public static final BlockEntry<DirectionalPartBlock> UNIVERSAL_ADAPTER = part("universal_adapter", "Universal Adapter",
            DirectionalShape.up(box(1, 0, 1, 15, 3, 15), box(3, 3, 3, 13, 10, 13)), Placement.AGAINST_FACE, MapColor.STONE);

    // full block with a rotor on top; null shape = full cube
    public static final BlockEntry<DirectionalPartBlock> BEARING_SERVO = part("bearing_servo", "Bearing Servo",
            null, Placement.TOWARDS_PLAYER, MapColor.STONE);

    private static final double[] PLATE = box(2, 0, 2, 14, 2, 14);

    public static final BlockEntry<DirectionalPartBlock> PANEL_BUTTON = control("panel_button", "Panel Button",
            box(5, 2, 5, 11, 5, 11));
    public static final BlockEntry<DirectionalPartBlock> PANEL_SWITCH = control("panel_switch", "Panel Switch",
            box(6, 2, 6, 10, 11, 10));
    public static final BlockEntry<DirectionalPartBlock> PANEL_KNOB = control("panel_knob", "Panel Knob",
            box(5, 2, 5, 11, 5, 11));
    public static final BlockEntry<DirectionalPartBlock> PANEL_SLIDER = control("panel_slider", "Panel Slider",
            box(2.5, 2, 6, 13.5, 5, 10));
    public static final BlockEntry<DirectionalPartBlock> PANEL_LAMP = control("panel_lamp", "Panel Lamp",
            box(5, 2, 5, 11, 6, 11));
    public static final BlockEntry<DirectionalPartBlock> NIXIE_DIGIT = control("nixie_digit", "Nixie Digit",
            box(4, 2, 5, 12, 14, 11));
    public static final BlockEntry<DirectionalPartBlock> PANEL_GAUGE = control("panel_gauge", "Panel Gauge",
            box(3, 2, 3, 13, 4, 13));
    public static final BlockEntry<DirectionalPartBlock> PANEL_KEYPAD = control("panel_keypad", "Panel Keypad",
            box(3, 2, 2, 13, 3, 14));

    private static BlockEntry<DirectionalPartBlock> control(String name, String english, double[] part) {
        return part(name, english, DirectionalShape.up(PLATE, part), Placement.AGAINST_FACE, MapColor.STONE);
    }

    private static BlockEntry<DirectionalPartBlock> part(String name, String english, DirectionalShape shape,
                                                        Placement placement, MapColor color) {
        return REGISTRATE
                .block(name, p -> new DirectionalPartBlock(p, shape, placement))
                .initialProperties(SharedProperties::stone)
                .properties(p -> {
                    p = p.mapColor(color).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
                    return shape == null ? p : p.noOcclusion();
                })
                .transform(TagGen.pickaxeOnly())
                .blockstate((c, p) -> p.directionalBlock(c.get(), existing(p, c.getName())))
                .lang(english)
                .item().model(ModBlocks::blockItemModel).build()
                .register();
    }

    private static double[] box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new double[] {x1, y1, z1, x2, y2, z2};
    }

    // models are authored by tools/models/gen_models.py, so datagen only points at them
    private static ModelFile existing(RegistrateBlockstateProvider p, String name) {
        return p.models().getExistingFile(p.modLoc("block/" + name));
    }

    private static <T extends Item> void blockItemModel(DataGenContext<Item, T> c, RegistrateItemModelProvider p) {
        p.withExistingParent(c.getName(), p.modLoc("block/" + c.getName()));
    }

    public static void register() {}

    private ModBlocks() {}
}
