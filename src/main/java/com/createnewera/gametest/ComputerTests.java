package com.createnewera.gametest;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.createnewera.computer.ConsoleLine;
import com.createnewera.network.EditorActionPayload;
import com.createnewera.registry.ModBlocks;

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
    // facing north: back is south (+z), left is east (+x)
    private static final BlockPos COMPUTER = new BlockPos(1, 1, 1);
    private static final BlockPos BACK = new BlockPos(1, 1, 2);
    private static final BlockPos LEFT = new BlockPos(2, 1, 1);

    private static BrassComputerBlockEntity run(GameTestHelper helper, String source) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.FACING, Direction.NORTH));
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.setSource(source);
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
        helper.setBlock(BACK, Blocks.REDSTONE_LAMP);
        var computer = run(helper, "redstone.setOutput('back', true) sleep(100)");
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(BACK, RedstoneLampBlock.LIT, true);
            if (!computer.isRunning())
                helper.fail("program should still be sleeping");
        });
    }

    @GameTest(template = EMPTY)
    public static void stoppingClearsOutputs(GameTestHelper helper) {
        helper.setBlock(BACK, Blocks.REDSTONE_LAMP);
        var computer = run(helper, "rs.setAnalogOutput('back', 9) while true do end");
        helper.runAfterDelay(10, () -> {
            helper.assertBlockProperty(BACK, RedstoneLampBlock.LIT, true);
            helper.assertBlockProperty(COMPUTER, BrassComputerBlock.RUNNING, true);
            computer.handleAction(null, EditorActionPayload.Action.STOP, computer.getSource());
            // the lamp turns off a few ticks after losing power
            helper.runAfterDelay(6, () -> {
                helper.assertBlockProperty(BACK, RedstoneLampBlock.LIT, false);
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
            expectPrinted(helper, computer, ConsoleLine.Kind.ERROR, "program:3:");
            helper.assertBlockProperty(COMPUTER, BrassComputerBlock.RUNNING, false);
        });
    }

    @GameTest(template = EMPTY)
    public static void compileErrorsAreReported(GameTestHelper helper) {
        var computer = run(helper, "while true\nprint('no do')");
        helper.succeedWhen(() -> expectPrinted(helper, computer, ConsoleLine.Kind.ERROR, "program:"));
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
}
