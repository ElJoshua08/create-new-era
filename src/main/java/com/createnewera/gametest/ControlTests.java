package com.createnewera.gametest;

import com.createnewera.CreateNewEra;
import com.createnewera.block.DirectionalShape;
import com.createnewera.block.control.NixieDigitBlock;
import com.createnewera.block.control.PanelControlBlockEntity;
import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelGaugeBlock;
import com.createnewera.block.control.PanelLampBlock;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusData;
import com.createnewera.bus.BusPort;
import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.createnewera.computer.ComputerFiles;
import com.createnewera.computer.ConsoleLine;
import com.createnewera.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Panel controls as bus devices: clicked by players, read and set by programs. */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class ControlTests {
    private static final String ROOM = "empty5";
    private static final BlockPos COMPUTER = new BlockPos(2, 1, 2);

    private static BrassComputerBlockEntity computer(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos power = new BlockPos(2, 1, 3);
        helper.setBlock(power, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(power);
        motor.generatedSpeed.setValue(256);
        return helper.getBlockEntity(COMPUTER);
    }

    /** A control on top of a stone block, cabled to the computer's own port. */
    private static BlockPos control(GameTestHelper helper, int x, int z, Block block) {
        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos at = new BlockPos(x, 1, z);
        helper.setBlock(at, block.defaultBlockState());
        BusData data = BusData.get(helper.getLevel());
        BusPort connection = Bus.controlAt(data.portsAt(helper.absolutePos(at)));
        if (connection == null)
            helper.fail(block + " came without its connection");
        BusPort own = data.portsAt(helper.absolutePos(COMPUTER)).get(0);
        if (data.connect(own.id(), connection.id()) == null)
            helper.fail("couldn't cable " + block);
        return at;
    }

    /** The device's name: names stick to the world, so a test can't assume its block's plain id. */
    private static String name(GameTestHelper helper, BlockPos at) {
        BlockPos abs = helper.absolutePos(at);
        return BusData.get(helper.getLevel()).nameOf(abs, helper.getLevel().getBlockState(abs).getBlock());
    }

    /** A click on a control, at a point of its model (authored facing up, in pixels). */
    private static void click(GameTestHelper helper, BlockPos at, double x, double y, double z) {
        BlockPos abs = helper.absolutePos(at);
        double[] p = DirectionalShape.map(x, y, z, Direction.UP);
        Vec3 hit = Vec3.atLowerCornerOf(abs).add(p[0] / 16, p[1] / 16, p[2] / 16);
        helper.useBlock(at, helper.makeMockPlayer(GameType.CREATIVE), new BlockHitResult(hit, Direction.UP, abs, false));
    }

    private static void expectPrinted(GameTestHelper helper, BrassComputerBlockEntity computer, String text) {
        if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().contains(text)))
            helper.fail("expected '" + text + "' in console " + computer.getConsole());
    }

    // a panel: the button lights the lamp and sets the nixie, the knob drives the gauge, the keypad is heard
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void aPanelDrivesAndShows(GameTestHelper helper) {
        var computer = computer(helper);
        BlockPos button = control(helper, 0, 0, ModBlocks.PANEL_BUTTON.get());
        BlockPos lamp = control(helper, 1, 0, ModBlocks.PANEL_LAMP.get());
        BlockPos nixie = control(helper, 3, 0, ModBlocks.NIXIE_DIGIT.get());
        BlockPos knob = control(helper, 4, 0, ModBlocks.PANEL_KNOB.get());
        BlockPos gauge = control(helper, 4, 4, ModBlocks.PANEL_GAUGE.get());
        BlockPos keypad = control(helper, 0, 4, ModBlocks.PANEL_KEYPAD.get());
        BlockPos speaker = control(helper, 2, 4, ModBlocks.PANEL_SPEAKER.get());
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local button, lamp = bus.get('" + name(helper, button) + "'), bus.get('" + name(helper, lamp) + "')",
                "local nixie, knob = bus.get('" + name(helper, nixie) + "'), bus.get('" + name(helper, knob) + "')",
                "local gauge = bus.get('" + name(helper, gauge) + "')",
                "button.onPress(function() lamp.set('green') nixie.setText('7') print('pressed') end)",
                "knob.onChange(function(value) gauge.set(value / 15) end)",
                "print({b = {2, 3}, a = 1, name = 'x'})",
                "bus.get('" + name(helper, keypad) + "').onKey(function(key) print('key ' .. key) end)",
                "local sp = bus.get('" + name(helper, speaker) + "')",
                "sp.playNote('bell', 12) sp.playSound('minecraft:block.bell.use', 0.5) sp.speak('hola') print('spoke')",
                "print('ready')"));
        computer.start("create_new_era.console.running");
        // the program needs a few ticks to set up its handlers first
        helper.runAfterDelay(30, () -> {
            expectPrinted(helper, computer, "ready");
            expectPrinted(helper, computer, "spoke");
            expectPrinted(helper, computer, "{2, 3}"); // print shows a table's contents
            expectPrinted(helper, computer, "{a = 1, b = {2, 3}, name = \"x\"}");
            click(helper, button, 8, 4, 8);
            // what the server does with a player's drag (DialDrag sends the step it's dragged to)
            ModBlocks.PANEL_KNOB.get().set(helper.getLevel(), helper.absolutePos(knob), 6);
            click(helper, keypad, 8, 3, 6.5); // the middle key of the second row
        });
        helper.runAfterDelay(60, () -> {
            expectPrinted(helper, computer, "pressed");
            expectPrinted(helper, computer, "key 5");
            var level = helper.getLevel();
            if (level.getBlockState(helper.absolutePos(lamp)).getValue(PanelLampBlock.LIGHT) != PanelLampBlock.Light.GREEN)
                helper.fail("the lamp didn't turn green");
            if (!(level.getBlockEntity(helper.absolutePos(nixie)) instanceof PanelControlBlockEntity tube) || !tube.glyph.equals("7"))
                helper.fail("the nixie doesn't show 7");
            if (level.getBlockState(helper.absolutePos(knob)).getValue(PanelDialBlock.VALUE) != 6)
                helper.fail("the knob didn't take the dragged value");
            if (level.getBlockState(helper.absolutePos(gauge)).getValue(PanelGaugeBlock.NEEDLE) != 6)
                helper.fail("the gauge didn't follow the knob");
            helper.succeed();
        });
    }

    // a program gives a slider its own range; dragging and setValue stay on its steps
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void slidersTakeTheRangeProgramsGive(GameTestHelper helper) {
        var computer = computer(helper);
        BlockPos slider = control(helper, 0, 0, ModBlocks.PANEL_SLIDER.get());
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local s = bus.get('" + name(helper, slider) + "')",
                "s.setRange(-1, 1, 0.1)",
                "s.setValue(0.5)",
                "print('range', s.getValue(), s.getMinValue(), s.getMaxValue(), s.getStep(), s.getFraction())",
                "s.onChange(function(value) print('moved', value) end)"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(30, () -> {
            expectPrinted(helper, computer, "range\t0.5\t-1\t1\t0.1\t0.75");
            // a player drags it to the top notch
            ModBlocks.PANEL_SLIDER.get().set(helper.getLevel(), helper.absolutePos(slider), 20);
        });
        helper.runAfterDelay(60, () -> {
            expectPrinted(helper, computer, "moved\t1");
            if (helper.getLevel().getBlockState(helper.absolutePos(slider)).getValue(PanelDialBlock.VALUE) != 15)
                helper.fail("the slider's block state should show it at the top");
            helper.succeed();
        });
    }

    // on solid ground the instruments read the ground: its height, level, facing north, still
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void instrumentsReadWhereTheyAre(GameTestHelper helper) {
        var computer = computer(helper);
        BlockPos alt = control(helper, 0, 0, ModBlocks.PANEL_ALTIMETER.get());
        BlockPos gyro = control(helper, 1, 0, ModBlocks.PANEL_GYROSCOPE.get());
        BlockPos compass = control(helper, 3, 0, ModBlocks.PANEL_COMPASS.get());
        BlockPos speed = control(helper, 4, 0, ModBlocks.PANEL_AIRSPEED.get());
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local a, g = bus.get('" + name(helper, alt) + "'), bus.get('" + name(helper, gyro) + "')",
                "local c, s = bus.get('" + name(helper, compass) + "'), bus.get('" + name(helper, speed) + "')",
                "print('alt', a.getAltitude(), a.getVerticalSpeed())",
                "print('gyro', g.getPitch(), g.getRoll(), g.getHeading())",
                "print('compass', c.getHeading(), c.getDirection())",
                "s.setMaxSpeed(20)",
                "print('speed', s.getSpeed(), s.getMaxSpeed())"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(30, () -> {
            double y = helper.absolutePos(alt).getY() + 0.5;
            expectPrinted(helper, computer, "alt\t" + y + "\t0");
            expectPrinted(helper, computer, "gyro\t0\t0\t0");
            expectPrinted(helper, computer, "compass\t0\tnorth");
            expectPrinted(helper, computer, "speed\t0\t20");
            helper.succeed();
        });
    }

    // nixies side by side make one display; a cable to the last drives the whole row
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void nixiesInARowShowText(GameTestHelper helper) {
        var computer = computer(helper);
        for (int x = 0; x < 3; x++) {
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 0), ModBlocks.NIXIE_DIGIT.getDefaultState());
        }
        BlockPos last = control(helper, 3, 0, ModBlocks.NIXIE_DIGIT.get());
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local n = bus.get('" + name(helper, last) + "')",
                "print('len', n.getLength())",
                "n.setText('HI!')",
                "print('text', n.getText())",
                "n.show(42)",
                "print('num', n.getText())",
                "n.setChar(1, 'A')",
                "print('char', n.getText(), n.getChar(1), n.getChar(2))",
                "n.show(3.14159, 2) print('dec', n.getText())",
                "n.setColor('lime') print('color', n.getColor())",
                "n.show(12345) print('over', n.getText())"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(30, () -> {
            expectPrinted(helper, computer, "len\t4");
            expectPrinted(helper, computer, "text\tHI!");
            expectPrinted(helper, computer, "num\t  42");
            expectPrinted(helper, computer, "char\tA 42\tA\tnil");
            expectPrinted(helper, computer, "dec\t3.14");
            expectPrinted(helper, computer, "color\tlime");
            expectPrinted(helper, computer, "over\t----");
            var first = (PanelControlBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(0, 1, 0)));
            if (!first.glyph.equals("-") || first.color != net.minecraft.world.item.DyeColor.LIME)
                helper.fail("the first tube, with no cable of its own, should show a lime -: " + first.glyph + " " + first.color);
            if (!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(0, 1, 0))).getValue(NixieDigitBlock.LIT))
                helper.fail("a lit tube gives light");
            helper.succeed();
        });
    }

    // knobs and sliders carry a block entity for their smooth drawing; the button pops back up by itself
    @GameTest(template = ROOM)
    public static void slidersAndButtonsMoveLikeTheirModels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.PANEL_SLIDER.getDefaultState());
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.PANEL_BUTTON.getDefaultState());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1))) instanceof com.createnewera.block.control.PanelControlBlockEntity))
            helper.fail("the slider has no block entity to draw its handle");
        click(helper, new BlockPos(3, 1, 1), 8, 4, 8);
        helper.runAfterDelay(15, () -> {
            if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 1, 1))).getValue(
                    com.createnewera.block.control.PanelButtonBlock.PRESSED))
                helper.fail("the button stayed down");
            helper.succeed();
        });
    }
}
