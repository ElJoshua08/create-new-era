package com.createnewera.gametest;

import com.createnewera.CreateNewEra;
import com.createnewera.block.DirectionalShape;
import com.createnewera.block.control.NixieDigitBlock;
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
                "button.onPress(function() lamp.set('green') nixie.setDigit(7) print('pressed') end)",
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
            if (level.getBlockState(helper.absolutePos(nixie)).getValue(NixieDigitBlock.DIGIT) != 7)
                helper.fail("the nixie doesn't show 7");
            if (level.getBlockState(helper.absolutePos(knob)).getValue(PanelDialBlock.VALUE) != 6)
                helper.fail("the knob didn't take the dragged value");
            if (level.getBlockState(helper.absolutePos(gauge)).getValue(PanelGaugeBlock.NEEDLE) != 6)
                helper.fail("the gauge didn't follow the knob");
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
