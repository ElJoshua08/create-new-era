package com.createnewera.gametest;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusData;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.PartGeometry;
import com.createnewera.bus.PortMode;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The data bus without cables or graphics: ports on blocks, spans between them, and what a
 * program on the computer can see and do through them.
 */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class BusTests {
    private static final String ROOM = "empty5";
    // the computer faces north with its driving motor behind it, out of the way of the devices
    private static final BlockPos COMPUTER = new BlockPos(2, 1, 2);
    private static final BlockPos POWER = new BlockPos(2, 1, 3);
    private static final BlockPos MOTOR = new BlockPos(0, 1, 0);
    private static final BlockPos CHEST = new BlockPos(4, 1, 2);

    private static BrassComputerBlockEntity computer(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(POWER, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity power = helper.getBlockEntity(POWER);
        power.generatedSpeed.setValue(256);
        return helper.getBlockEntity(COMPUTER);
    }

    private static void motor(GameTestHelper helper) {
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
    }

    private static void chest(GameTestHelper helper, BlockPos pos, int iron) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, iron));
    }

    private static BusPort port(GameTestHelper helper, BlockPos pos, PortMode mode) {
        if (pos.equals(COMPUTER))
            return socketPort(helper, mode);
        // inputs and outputs on different spots, so a block can carry one of each
        BusPort port = Bus.placePort(helper.getLevel(), helper.absolutePos(pos), Direction.UP, mode == PortMode.OUT ? 8 : 4, 8, mode);
        if (port == null)
            helper.fail("couldn't place a port on " + pos);
        return port;
    }

    /** A port in the computer's next free socket. */
    private static BusPort socketPort(GameTestHelper helper, PortMode mode) {
        BlockPos pc = helper.absolutePos(COMPUTER);
        var here = BusData.get(helper.getLevel()).portsAt(pc);
        for (var socket : BrassComputerBlock.sockets(helper.getLevel().getBlockState(pc), pc))
            if (here.stream().noneMatch(socket::takenBy)) {
                BusPort port = Bus.placePort(helper.getLevel(), pc, socket.face(), socket.u(), socket.v(), mode);
                if (port == null)
                    helper.fail("a free socket refused its port: " + socket);
                return port;
            }
        helper.fail("no free socket");
        return null;
    }

    private static void link(GameTestHelper helper, BusPort a, BusPort b) {
        if (BusData.get(helper.getLevel()).connect(a.id(), b.id()) == null)
            helper.fail("couldn't connect " + a + " and " + b);
    }

    private static void run(BrassComputerBlockEntity computer, String program) {
        computer.setProgram(ComputerFiles.STARTUP, program);
        computer.start("create_new_era.console.running");
    }

    private static void expectPrinted(GameTestHelper helper, BrassComputerBlockEntity computer, ConsoleLine.Kind kind, String text) {
        if (computer.getConsole().stream().noneMatch(l -> l.kind() == kind && l.text().contains(text)))
            helper.fail("expected " + kind + " '" + text + "' in console " + computer.getConsole());
    }

    // the list is in distance order, and only holds what's actually connected
    @GameTest(template = ROOM)
    public static void listsConnectedDevicesNearestFirst(GameTestHelper helper) {
        var computer = computer(helper);
        motor(helper);
        chest(helper, CHEST, 1);
        chest(helper, new BlockPos(0, 1, 4), 1);
        BusPort pc = port(helper, COMPUTER, PortMode.IN);
        BusPort motorPort = port(helper, MOTOR, PortMode.OUT);
        BusPort chestPort = port(helper, CHEST, PortMode.OUT);
        port(helper, new BlockPos(0, 1, 4), PortMode.OUT); // a port, but no cable to it
        link(helper, pc, motorPort);
        link(helper, motorPort, chestPort);
        run(computer, "for _, d in ipairs(bus.list()) do print(d.type, d.distance) end print('count', #bus.list())");
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "count\t2");
            var lines = computer.getConsole().stream().filter(l -> l.kind() == ConsoleLine.Kind.OUTPUT).map(ConsoleLine::text).toList();
            if (!lines.get(0).startsWith("minecraft:chest\t2") || !lines.get(1).startsWith("create:creative_motor\t2.8"))
                helper.fail("wrong order or distances: " + lines);
        });
    }

    // output ports let programs read: speed from anything that turns, contents from inventories
    @GameTest(template = ROOM)
    public static void readsThroughOutputPorts(GameTestHelper helper) {
        var computer = computer(helper);
        motor(helper);
        chest(helper, CHEST, 5);
        BusPort pc = port(helper, COMPUTER, PortMode.IN);
        link(helper, pc, port(helper, MOTOR, PortMode.OUT));
        link(helper, pc, port(helper, CHEST, PortMode.OUT));
        String motorName = BusData.get(helper.getLevel()).nameOf(helper.absolutePos(MOTOR), AllBlocks.CREATIVE_MOTOR.get());
        String chestName = BusData.get(helper.getLevel()).nameOf(helper.absolutePos(CHEST), Blocks.CHEST);
        // a motor placed this tick starts turning on the next
        run(computer, "sleep(0.25) local m = bus.get('" + motorName + "') print('speed', m.getSpeed(), m:isOverstressed())\n"
                + "local c = bus.get('" + chestName + "') print('iron', c.count('minecraft:iron_ingot'), c.items()['minecraft:iron_ingot'])");
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "speed\t16\tfalse");
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "iron\t5\t5");
        });
    }

    // orders need an input port; with only an output the program is told which port is missing
    @GameTest(template = ROOM)
    public static void ordersNeedAnInputPort(GameTestHelper helper) {
        var computer = computer(helper);
        motor(helper);
        BusPort pc = port(helper, COMPUTER, PortMode.IN);
        BusPort motorPort = port(helper, MOTOR, PortMode.OUT);
        link(helper, pc, motorPort);
        String name = BusData.get(helper.getLevel()).nameOf(helper.absolutePos(MOTOR), AllBlocks.CREATIVE_MOTOR.get());
        run(computer, "bus.get('" + name + "').setSpeed(64)");
        helper.runAfterDelay(10, () -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.ERROR, "needs an input port");
            // the wrench turns the same port around
            BusData.get(helper.getLevel()).setMode(motorPort.id(), PortMode.IN);
            run(computer, "bus.get('" + name + "').setSpeed(64)");
            helper.succeedWhen(() -> {
                CreativeMotorBlockEntity motor = helper.getBlockEntity(MOTOR);
                if (motor.generatedSpeed.getValue() != 64)
                    helper.fail("motor speed is " + motor.generatedSpeed.getValue());
            });
        });
    }

    // onChange keeps the program listening after its main code ends, and runs when the device changes
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void onChangeRunsWhenADeviceChanges(GameTestHelper helper) {
        var computer = computer(helper);
        motor(helper);
        BusPort pc = port(helper, COMPUTER, PortMode.IN);
        link(helper, pc, port(helper, MOTOR, PortMode.OUT));
        link(helper, pc, port(helper, MOTOR, PortMode.IN));
        String name = BusData.get(helper.getLevel()).nameOf(helper.absolutePos(MOTOR), AllBlocks.CREATIVE_MOTOR.get());
        run(computer, "local m = bus.get('" + name + "')\nm.onChange(function(d) print('target', d.getTargetSpeed()) end)\nprint('listening')");
        helper.runAfterDelay(10, () -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "listening");
            if (!computer.isRunning())
                helper.fail("a program with handlers should keep running");
            CreativeMotorBlockEntity motor = helper.getBlockEntity(MOTOR);
            motor.generatedSpeed.setValue(32);
            helper.succeedWhen(() -> expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "target\t32"));
        });
    }

    // automatic names never clash, renames are checked, and a broken block leaves the network
    @GameTest(template = ROOM)
    public static void namesAndBrokenDevices(GameTestHelper helper) {
        var computer = computer(helper);
        BlockPos second = new BlockPos(4, 1, 4);
        chest(helper, CHEST, 1);
        chest(helper, second, 1);
        BusData data = BusData.get(helper.getLevel());
        String a = data.nameOf(helper.absolutePos(CHEST), Blocks.CHEST);
        String b = data.nameOf(helper.absolutePos(second), Blocks.CHEST);
        if (a.equals(b) || !a.matches("chest(_\\d+)?") || !b.matches("chest(_\\d+)?"))
            helper.fail("bad automatic names " + a + ", " + b);
        if (data.rename(helper.absolutePos(second), a) || data.rename(helper.absolutePos(second), "Bad Name"))
            helper.fail("accepted a taken or malformed name");
        String fresh = "almacen_" + helper.absolutePos(second).asLong();
        fresh = fresh.replace('-', 'm');
        if (!data.rename(helper.absolutePos(second), fresh))
            helper.fail("refused a good name");

        BusPort pc = port(helper, COMPUTER, PortMode.IN);
        link(helper, pc, port(helper, CHEST, PortMode.OUT));
        link(helper, pc, port(helper, second, PortMode.OUT));
        helper.destroyBlock(CHEST);
        String expected = fresh;
        run(computer, "for _, d in ipairs(bus.list()) do print('has', d.name) end");
        helper.succeedWhen(() -> {
            expectPrinted(helper, computer, ConsoleLine.Kind.OUTPUT, "has\t" + expected);
            if (computer.getConsole().stream().anyMatch(l -> l.text().equals("has\t" + a)))
                helper.fail("broken chest still listed");
            if (!data.portsAt(helper.absolutePos(CHEST)).isEmpty())
                helper.fail("broken chest kept its ports");
        });
    }

    // a computer has room for a few ports before it needs an expansion
    @GameTest(template = ROOM)
    public static void computersHaveLimitedPorts(GameTestHelper helper) {
        computer(helper);
        var level = helper.getLevel();
        BlockPos pc = helper.absolutePos(COMPUTER);
        var sockets = BrassComputerBlock.sockets(level.getBlockState(pc), pc);
        // facing north, its sockets are on the east and west sides, two each
        if (sockets.stream().filter(s -> s.face() == Direction.EAST).count() != 2 || sockets.stream().filter(s -> s.face() == Direction.WEST).count() != 2)
            helper.fail("sockets on the wrong faces: " + sockets);
        if (Bus.placePort(level, pc, Direction.UP, 6, 6, PortMode.IN) != null)
            helper.fail("a port went on top while the sockets were free");
        // a click anywhere on a side snaps into the nearest free socket there
        BusPort snapped = Bus.placePort(level, pc, Direction.EAST, 0, 0, PortMode.IN);
        if (snapped == null || sockets.stream().noneMatch(s -> s.face() == Direction.EAST && s.u() == snapped.u() && s.v() == snapped.v()))
            helper.fail("didn't snap into a socket: " + snapped);
        for (int i = 1; i < Bus.COMPUTER_PORTS; i++)
            socketPort(helper, PortMode.IN);
        if (Bus.placePort(level, pc, Direction.UP, 6, 6, PortMode.IN) != null)
            helper.fail("took more ports than it has room for");
        helper.succeed();
    }

    // a click anywhere on any face lands inside the part it places, so what you see is what you clicked
    @GameTest(template = ROOM)
    public static void partsLandWhereYouClick(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Direction face : Direction.values()) {
            for (double[] at : new double[][] {{0.5, 0.5}, {0.1, 0.1}, {0.9, 0.2}, {0.3, 0.95}}) {
                Vec3 hit = pointOnFace(pos, face, at[0], at[1]);
                int[] spot = PartGeometry.spotAt(pos, face, hit, BusPort.Kind.PORT.size);
                BusPort part = new BusPort(0, pos, face, spot[0], spot[1], BusPort.Kind.PORT, PortMode.OUT);
                if (!PartGeometry.box(part).inflate(1 / 16.0).contains(hit))
                    helper.fail("click " + hit + " on " + face + " placed a port at " + PartGeometry.box(part));
                if (PartGeometry.partAt(java.util.List.of(part), face, hit) == null && at[0] > 0.2 && at[0] < 0.8 && at[1] > 0.2 && at[1] < 0.8)
                    helper.fail("couldn't find the port just placed under " + hit + " on " + face);
            }
            if (PartGeometry.spotAt(pos, face, pointOnFace(pos, face, 0.5, 0.5), 4)[0] != 6)
                helper.fail("a centred click should centre the port on " + face);
        }
        helper.succeed();
    }

    /** A point on a block's face, with s and t running across it from one corner, 0 to 1. */
    private static Vec3 pointOnFace(BlockPos pos, Direction face, double s, double t) {
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        return switch (face.getAxis()) {
            case Y -> corner.add(s, face == Direction.UP ? 1 : 0, t);
            case Z -> corner.add(s, t, face == Direction.SOUTH ? 1 : 0);
            case X -> corner.add(face == Direction.EAST ? 1 : 0, t, s);
        };
    }

    // expansions only go on computers, add ports, and run out
    @GameTest(template = ROOM)
    public static void expansionsAddComputerPorts(GameTestHelper helper) {
        computer(helper);
        chest(helper, CHEST, 1);
        var level = helper.getLevel();
        BlockPos pc = helper.absolutePos(COMPUTER);
        if (Bus.placePart(level, helper.absolutePos(CHEST), Direction.UP, 0, 0, BusPort.Kind.EXPANSION, PortMode.OUT) != null)
            helper.fail("an expansion went on a chest");
        for (int i = 0; i < Bus.COMPUTER_PORTS; i++)
            socketPort(helper, PortMode.IN);
        if (Bus.placePort(level, pc, Direction.UP, 0, 4, PortMode.IN) != null)
            helper.fail("a fifth port fit without an expansion");
        if (Bus.placePart(level, pc, Direction.WEST, 6, 4, BusPort.Kind.EXPANSION, PortMode.OUT) != null)
            helper.fail("an expansion covered a socket");
        if (Bus.placePart(level, pc, Direction.UP, 0, 0, BusPort.Kind.EXPANSION, PortMode.OUT) == null)
            helper.fail("the expansion didn't go on");
        if (Bus.placePort(level, pc, Direction.UP, 0, 8, PortMode.IN) == null)
            helper.fail("the expansion added no ports");
        // overlapping the expansion is refused
        if (Bus.placePort(level, pc, Direction.UP, 2, 2, PortMode.IN) != null)
            helper.fail("a port went on top of the expansion");
        Bus.placePart(level, pc, Direction.UP, 10, 0, BusPort.Kind.EXPANSION, PortMode.OUT);
        Bus.placePart(level, pc, Direction.DOWN, 0, 0, BusPort.Kind.EXPANSION, PortMode.OUT);
        if (Bus.placePart(level, pc, Direction.DOWN, 8, 8, BusPort.Kind.EXPANSION, PortMode.OUT) != null)
            helper.fail("took more than " + Bus.MAX_EXPANSIONS + " expansions");
        helper.succeed();
    }

    // the wrench turns a port around; other parts have no direction
    @GameTest(template = ROOM)
    public static void wrenchFlipsPorts(GameTestHelper helper) {
        chest(helper, CHEST, 1);
        var level = helper.getLevel();
        BusPort port = Bus.placePort(level, helper.absolutePos(CHEST), Direction.UP, 4, 4, PortMode.OUT);
        Bus.flip(level, port);
        if (BusData.get(level).port(port.id()).mode() != PortMode.IN)
            helper.fail("the wrench didn't turn the port");
        Bus.removePart(level, BusData.get(level).port(port.id()));
        if (!BusData.get(level).portsAt(helper.absolutePos(CHEST)).isEmpty())
            helper.fail("the port didn't come off");
        helper.succeed();
    }
}
