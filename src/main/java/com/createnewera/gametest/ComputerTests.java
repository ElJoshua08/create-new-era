package com.createnewera.gametest;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.createnewera.computer.ComputerFiles;
import com.createnewera.computer.ConsoleLine;
import com.createnewera.network.EditorActionPayload;
import com.createnewera.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The computer running real Lua: output, redstone both ways, sleeping, errors and runaway loops. */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class ComputerTests {
    private static final String EMPTY = "empty3";
    // facing north: front is north (-z), back is south (+z) where the motor drives it, left is east (+x)
    private static final BlockPos COMPUTER = new BlockPos(1, 1, 1);
    private static final BlockPos FRONT = new BlockPos(1, 1, 0);
    private static final BlockPos MOTOR = new BlockPos(1, 1, 2);
    private static final BlockPos LEFT = new BlockPos(2, 1, 1);

    private static void placeComputer(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** A creative motor behind the computer, turning at the given speed. */
    private static void power(GameTestHelper helper, int rpm) {
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(MOTOR);
        motor.generatedSpeed.setValue(rpm);
    }

    private static BrassComputerBlockEntity run(GameTestHelper helper, String source) {
        placeComputer(helper);
        power(helper, 256);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.setProgram(ComputerFiles.STARTUP, source);
        computer.start("create_new_era.console.running");
        return computer;
    }

    private static boolean printed(BrassComputerBlockEntity computer, ConsoleLine.Kind kind, String text) {
        return computer.getConsole().stream().anyMatch(l -> l.kind() == kind && l.text().contains(text));
    }

    private static void expectPrinted(GameTestHelper helper, BrassComputerBlockEntity computer, ConsoleLine.Kind kind, String text) {
        if (!printed(computer, kind, text))
            helper.fail("expected " + kind + " '" + text + "' in console " + computer.getConsole());
    }

    @GameTest(template = EMPTY)
    public static void printsAndFinishes(GameTestHelper helper) {
        var computer = run(helper, "print('hola', 1 + 1, nil, true)");
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "hola\t2\tnil\ttrue");
            expectPrinted(helper, computer, ConsoleLine.Kind.SYSTEM, "create_new_era.console.finished");
            helper.assertBlockProperty(COMPUTER, BrassComputerBlock.RUNNING, false);
        });
    }

    @GameTest(template = EMPTY)
    public static void drivesRedstoneOut(GameTestHelper helper) {
        helper.setBlock(FRONT, Blocks.REDSTONE_LAMP);
        var computer = run(helper, "redstone.setOutput('front', true) sleep(100)");
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(FRONT, RedstoneLampBlock.LIT, true);
            if (!computer.isRunning())
                helper.fail("program should still be sleeping");
        });
    }

    @GameTest(template = EMPTY)
    public static void stoppingClearsOutputs(GameTestHelper helper) {
        helper.setBlock(FRONT, Blocks.REDSTONE_LAMP);
        var computer = run(helper, "rs.setAnalogOutput('front', 9) while true do end");
        helper.runAfterDelay(10, () -> {
            helper.assertBlockProperty(FRONT, RedstoneLampBlock.LIT, true);
            helper.assertBlockProperty(COMPUTER, BrassComputerBlock.RUNNING, true);
            computer.handleAction(null, EditorActionPayload.Action.STOP, "", "");
            // the lamp turns off a few ticks after losing power
            helper.runAfterDelay(6, () -> {
                helper.assertBlockProperty(FRONT, RedstoneLampBlock.LIT, false);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY)
    public static void readsRedstoneIn(GameTestHelper helper) {
        helper.setBlock(LEFT, Blocks.REDSTONE_BLOCK);
        var computer = run(helper, "print(redstone.getAnalogInput('left'), rs.getInput('right'))");
        helper.succeedWhen(() -> expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "15\tfalse"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void sleepWaitsInGameTime(GameTestHelper helper) {
        var computer = run(helper, "sleep(1) print('awake', os.clock())");
        helper.runAfterDelay(10, () -> {
            if (printed(computer, ConsoleLine.Kind.OUTPUT, "awake"))
                helper.fail("woke up after half a second");
            helper.succeedWhen(() -> expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "awake\t1"));
        });
    }

    @GameTest(template = EMPTY)
    public static void errorsPointAtTheLine(GameTestHelper helper) {
        var computer = run(helper, "print('ok')\nlocal t = nil\nt.x = 1");
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.ERROR, "startup.lua:3:");
            helper.assertBlockProperty(COMPUTER, BrassComputerBlock.RUNNING, false);
        });
    }

    @GameTest(template = EMPTY)
    public static void compileErrorsAreReported(GameTestHelper helper) {
        var computer = run(helper, "while true\nprint('no do')");
        helper.succeedWhen(() -> expectPrinted(helper, computer, ConsoleLine.Kind.ERROR, "startup.lua:"));
    }

    // far more work than one tick's slice: the program has to be suspended and resumed many times
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void longComputationSpansTicks(GameTestHelper helper) {
        var computer = run(helper, "local n = 0 for i = 1, 3e6 do n = n + 1 end print('done', n)");
        long start = helper.getTick();
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "done\t3000000");
            if (helper.getTick() - start < 2)
                helper.fail("finished in a single tick, so the time slice isn't being enforced");
        });
    }

    // modules load from other files on the same computer, once, and errors name the file they came from
    @GameTest(template = EMPTY)
    public static void requireLoadsOtherFiles(GameTestHelper helper) {
        placeComputer(helper);
        power(helper, 256);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.handleAction(null, EditorActionPayload.Action.SAVE, "lib/greet.lua",
                "print('loading') return { hi = function(n) return 'hi ' .. n end }");
        computer.handleAction(null, EditorActionPayload.Action.SAVE, "broken.lua", "local x = nil\nreturn x.y");
        computer.setProgram("main.lua", "local g = require('lib.greet')\nrequire('lib/greet.lua')\nprint(g.hi('bob'))\nrequire('broken')");
        computer.start("create_new_era.console.running");
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "hi bob");
            expectPrinted(helper, computer, ConsoleLine.Kind.ERROR, "broken.lua:2:");
            long loads = computer.getConsole().stream().filter(l -> l.text().equals("loading")).count();
            if (loads != 1)
                helper.fail("module ran " + loads + " times");
        });
    }

    // no rotation, no progress: the program waits where it is and carries on once the shaft turns
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pausesWithoutRotation(GameTestHelper helper) {
        placeComputer(helper);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.setProgram(ComputerFiles.STARTUP, "print('speed', math.abs(os.speed()))");
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(20, () -> {
            if (printed(computer, ConsoleLine.Kind.OUTPUT, "speed"))
                helper.fail("ran without any rotation");
            if (!computer.isRunning())
                helper.fail("a paused program should stay loaded");
            power(helper, 64);
            helper.succeedWhen(() -> expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "speed\t64"));
        });
    }

    // the slice of each tick follows the speed: 256 RPM gets the most, half speed half as much
    @GameTest(template = EMPTY)
    public static void speedSetsTheTimeSlice(GameTestHelper helper) {
        placeComputer(helper);
        power(helper, 128);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        helper.succeedWhen(() -> {
            long budget = computer.budgetNanos();
            if (budget != BrassComputerBlockEntity.MAX_BUDGET_NANOS / 2)
                helper.fail("128 RPM should get half the time, got " + budget + " ns");
        });
    }
}
