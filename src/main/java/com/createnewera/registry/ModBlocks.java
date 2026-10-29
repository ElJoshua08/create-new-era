package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.createnewera.block.BrassScreenBlock;
import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.DirectionalPartBlock.Placement;
import com.createnewera.block.DirectionalShape;
import com.createnewera.block.HorizontalMachineBlock;
import com.createnewera.block.control.NixieDigitBlock;
import com.createnewera.block.control.PanelButtonBlock;
import com.createnewera.block.control.PanelControlBlock;
import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelGaugeBlock;
import com.createnewera.block.control.PanelKeypadBlock;
import com.createnewera.block.control.PanelLampBlock;
import com.createnewera.block.control.PanelSwitchBlock;
import com.createnewera.computer.BrassComputerBlock;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;

public final class ModBlocks {
    static {
        REGISTRATE.setCreativeTab(ModCreativeTabs.MAIN);
    }

    public static final BlockEntry<BrassComputerBlock> BRASS_COMPUTER = REGISTRATE
            .block("brass_computer", BrassComputerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            // not occluding: light has to reach inside it, or the shaft turning in its back is drawn black
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> s.getValue(BrassComputerBlock.RUNNING) ? 4 : 0))
            .transform(TagGen.pickaxeOnly())
            .onRegister(b -> BlockStressValues.IMPACTS.register(b, () -> BrassComputerBlock.STRESS_IMPACT))
            // the screen is lit only while a program runs
            .blockstate((c, p) -> p.horizontalBlock(c.get(),
                    s -> existing(p, s.getValue(BrassComputerBlock.RUNNING) ? "brass_computer" : "brass_computer_off")))
            .lang("Brass Computer")
            .item().model(ModBlocks::blockItemModel).build()
            .register();

    public static final BlockEntry<BrassScreenBlock> BRASS_SCREEN = REGISTRATE
            .block("brass_screen", BrassScreenBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops())
            .transform(TagGen.pickaxeOnly())
            .blockstate(ModBlocks::screenBlockstate)
            .lang("Brass Screen")
            .item().model(ModBlocks::blockItemModel).build()
            .register();

    public static final BlockEntry<DirectionalPartBlock> UNIVERSAL_ADAPTER = part("universal_adapter", "Universal Adapter",
            DirectionalShape.up(box(1, 0, 1, 15, 3, 15), box(3, 3, 3, 13, 10, 13)), Placement.AGAINST_FACE, MapColor.STONE);

    // full block with a rotor on top; null shape = full cube
    public static final BlockEntry<DirectionalPartBlock> BEARING_SERVO = part("bearing_servo", "Bearing Servo",
            null, Placement.TOWARDS_PLAYER, MapColor.STONE);

    private static final double[] PLATE = box(2, 0, 2, 14, 2, 14);

    public static final BlockEntry<PanelButtonBlock> PANEL_BUTTON = control("panel_button", "Panel Button",
            box(5, 2, 5, 11, 5, 11), PanelButtonBlock::new,
            s -> s.getValue(PanelButtonBlock.PRESSED) ? "panel_button_pressed" : "panel_button");
    public static final BlockEntry<PanelSwitchBlock> PANEL_SWITCH = control("panel_switch", "Panel Switch",
            box(6, 2, 6, 10, 11, 10), PanelSwitchBlock::new,
            s -> s.getValue(PanelSwitchBlock.ON) ? "panel_switch_on" : "panel_switch");
    public static final BlockEntry<PanelDialBlock> PANEL_KNOB = control("panel_knob", "Panel Knob",
            box(5, 2, 5, 11, 5, 11), (p, shape) -> new PanelDialBlock(p, shape, PanelDialBlock.Style.KNOB),
            s -> "panel_knob_base");
    public static final BlockEntry<PanelDialBlock> PANEL_SLIDER = control("panel_slider", "Panel Slider",
            box(2.5, 2, 6, 13.5, 5, 10), (p, shape) -> new PanelDialBlock(p, shape, PanelDialBlock.Style.SLIDER),
            s -> "panel_slider_base");
    public static final BlockEntry<PanelLampBlock> PANEL_LAMP = control("panel_lamp", "Panel Lamp",
            box(5, 2, 5, 11, 6, 11), PanelLampBlock::new,
            s -> "panel_lamp_" + s.getValue(PanelLampBlock.LIGHT).getSerializedName());
    public static final BlockEntry<NixieDigitBlock> NIXIE_DIGIT = control("nixie_digit", "Nixie Digit",
            box(4, 2, 5, 12, 14, 11), NixieDigitBlock::new,
            s -> "nixie_digit_" + (s.getValue(NixieDigitBlock.DIGIT) == NixieDigitBlock.DARK ? "off" : s.getValue(NixieDigitBlock.DIGIT)));
    public static final BlockEntry<PanelGaugeBlock> PANEL_GAUGE = control("panel_gauge", "Panel Gauge",
            box(3, 2, 3, 13, 4, 13), PanelGaugeBlock::new,
            s -> "panel_gauge_" + s.getValue(PanelGaugeBlock.NEEDLE));
    public static final BlockEntry<PanelKeypadBlock> PANEL_KEYPAD = control("panel_keypad", "Panel Keypad",
            box(3, 2, 2, 13, 3, 14), PanelKeypadBlock::new, s -> "panel_keypad");

    /**
     * A panel control on its andesite plate. Its model follows its state (a pressed button, a
     * knob's setting...), turned to whichever face it was put on.
     */
    private static <T extends PanelControlBlock> BlockEntry<T> control(String name, String english, double[] part,
            BiFunction<BlockBehaviour.Properties, DirectionalShape, T> factory, Function<BlockState, String> model) {
        DirectionalShape shape = DirectionalShape.up(PLATE, part);
        return REGISTRATE
                .block(name, p -> factory.apply(p, shape))
                .initialProperties(SharedProperties::stone)
                .properties(p -> p.mapColor(MapColor.STONE).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion())
                .transform(TagGen.pickaxeOnly())
                .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> {
                    Direction facing = state.getValue(DirectionalPartBlock.FACING);
                    return ConfiguredModel.builder().modelFile(existing(p, model.apply(state)))
                            .rotationX(facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0)
                            .rotationY(facing.getAxis().isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360)
                            .build();
                }))
                .lang(english)
                .item().model(ModBlocks::blockItemModel).build()
                .register();
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

    /** Core plus, per side and corner, either the brass frame or glass; see gen_models.py. */
    private static void screenBlockstate(DataGenContext<Block, BrassScreenBlock> c, RegistrateBlockstateProvider p) {
        var builder = p.getMultipartBuilder(c.get());
        Map<String, BooleanProperty> edges = Map.of(
                "up", BrassScreenBlock.UP, "down", BrassScreenBlock.DOWN,
                "left", BrassScreenBlock.LEFT, "right", BrassScreenBlock.RIGHT);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int y = ((int) facing.toYRot() + 180) % 360;
            builder.part().modelFile(existing(p, "brass_screen_core")).rotationY(y).addModel()
                    .condition(HorizontalMachineBlock.FACING, facing).end();
            edges.forEach((name, joined) -> {
                screenPart(builder, p, "frame_" + name, y, facing).condition(joined, false).end();
                screenPart(builder, p, "glass_" + name, y, facing).condition(joined, true).end();
            });
            for (String vertical : new String[] {"up", "down"}) {
                for (String horizontal : new String[] {"left", "right"}) {
                    String corner = vertical + "_" + horizontal;
                    BooleanProperty v = edges.get(vertical), h = edges.get(horizontal);
                    // frame unless both adjoining sides are joined, split in two parts to avoid an OR
                    screenPart(builder, p, "frame_" + corner, y, facing).condition(v, false).end();
                    screenPart(builder, p, "frame_" + corner, y, facing).condition(v, true).condition(h, false).end();
                    screenPart(builder, p, "glass_" + corner, y, facing).condition(v, true).condition(h, true).end();
                }
            }
        }
    }

    private static MultiPartBlockStateBuilder.PartBuilder screenPart(MultiPartBlockStateBuilder builder,
            RegistrateBlockstateProvider p, String piece, int y, Direction facing) {
        return builder.part().modelFile(existing(p, "brass_screen_" + piece)).rotationY(y).addModel()
                .condition(HorizontalMachineBlock.FACING, facing);
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
