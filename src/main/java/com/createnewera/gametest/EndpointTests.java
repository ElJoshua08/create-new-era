package com.createnewera.gametest;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusData;
import com.createnewera.bus.BusPort;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The functions particular blocks offer on the bus, called from a real program. */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class EndpointTests {
    private static final BlockPos COMPUTER = new BlockPos(2, 1, 2);

    /** A brass port on the block's top, cabled straight to one of the computer's own ports. */
    private static String device(GameTestHelper helper, BlockPos at, int socket) {
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(at);
        BusPort port = Bus.placePart(level, abs, Direction.UP, 6, 6, BusPort.Kind.BRASS_PORT, PortMode.OUT);
        if (port == null)
            helper.fail("no port on " + at);
        BusData data = BusData.get(level);
        data.connect(data.portsAt(helper.absolutePos(COMPUTER)).get(socket).id(), port.id());
        return data.nameOf(abs, level.getBlockState(abs).getBlock());
    }

    // an input port powers its block like a lever: a lamp lights, a clutch disengages
    @GameTest(template = "empty5", timeoutTicks = 160)
    public static void portsPowerTheirBlocks(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(2, 1, 3), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(2, 1, 3));
        motor.generatedSpeed.setValue(256);
        BlockPos lamp = new BlockPos(0, 1, 0), clutch = new BlockPos(4, 1, 0);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(clutch, AllBlocks.CLUTCH.getDefaultState());
        var level = helper.getLevel();
        String lampName = input(helper, lamp, 0), clutchName = input(helper, clutch, 1);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local lamp, clutch = bus.get('" + lampName + "'), bus.get('" + clutchName + "')",
                "lamp.setRedstone(15)",
                "clutch.setRedstone(true)",
                "print('out', lamp.getRedstoneOutput(), clutch.getRedstoneOutput())",
                "sleep(0.5)",
                "lamp.setRedstone(0)",
                "clutch.setRedstone(false)",
                "print('off')",
                "lamp.setRedstone(99)"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(6, () -> {
            if (!level.getBlockState(helper.absolutePos(lamp)).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT))
                helper.fail("the lamp should be lit by its port");
            if (!level.getBlockState(helper.absolutePos(clutch)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))
                helper.fail("the clutch should be powered by its port");
        });
        helper.runAfterDelay(60, () -> {
            boolean said = computer.getConsole().stream().anyMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals("out\t15\t15"));
            if (!said)
                helper.fail("expected 'out 15 15' in " + computer.getConsole());
            if (level.getBlockState(helper.absolutePos(lamp)).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT))
                helper.fail("the lamp should go out when its port stops");
            if (level.getBlockState(helper.absolutePos(clutch)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))
                helper.fail("the clutch should be released");
            // a wrong argument points at the program's own line, not the prelude's
            boolean blamed = computer.getConsole().stream().anyMatch(l -> l.kind() == ConsoleLine.Kind.ERROR
                    && l.text().contains("startup.lua:9") && l.text().contains("setRedstone"));
            if (!blamed)
                helper.fail("the error should point at startup.lua:9: " + computer.getConsole());
            helper.succeed();
        });
    }

    private static BrassComputerBlockEntity poweredComputer(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(2, 1, 3), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(2, 1, 3));
        motor.generatedSpeed.setValue(256);
        return helper.getBlockEntity(COMPUTER);
    }

    /** A brass port on one face of the block, cabled to the computer. */
    private static String brassPort(GameTestHelper helper, BlockPos at, Direction face, int socket) {
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(at);
        BusPort port = Bus.placePart(level, abs, face, 6, 6, BusPort.Kind.BRASS_PORT, PortMode.OUT);
        if (port == null)
            helper.fail("no port on " + at + " " + face);
        BusData data = BusData.get(level);
        data.connect(data.portsAt(helper.absolutePos(COMPUTER)).get(socket).id(), port.id());
        return data.nameOf(abs, level.getBlockState(abs).getBlock());
    }

    private static void expectLine(GameTestHelper helper, BrassComputerBlockEntity computer, String line) {
        if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals(line)))
            helper.fail("expected '" + line + "' in " + computer.getConsole());
    }

    // Create's machines from a program: a bearing builds and drops its contraption, a sequencer takes a step
    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void createMachinesTakeOrders(GameTestHelper helper) {
        var computer = poweredComputer(helper);
        BlockPos motor = new BlockPos(0, 1, 0), bearing = new BlockPos(0, 2, 0), sequencer = new BlockPos(4, 1, 0);
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        ((CreativeMotorBlockEntity) helper.getBlockEntity(motor)).generatedSpeed.setValue(16);
        helper.setBlock(bearing, AllBlocks.MECHANICAL_BEARING.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
        helper.setBlock(new BlockPos(0, 3, 0), Blocks.OAK_PLANKS);
        helper.setBlock(sequencer, AllBlocks.SEQUENCED_GEARSHIFT.getDefaultState());
        String b = brassPort(helper, bearing, Direction.NORTH, 0), s = brassPort(helper, sequencer, Direction.UP, 1);
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local b, s = bus.get('" + b + "'), bus.get('" + s + "')",
                "b.assemble()",
                "sleep(0.5)",
                "print('bearing', b.isAssembled())",
                "b.disassemble()",
                "sleep(0.2)",
                "print('dropped', b.isAssembled())",
                "s.rotate(90, 'fast')",
                "print('sequencer', s.isRunning())"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(80, () -> {
            expectLine(helper, computer, "bearing\ttrue");
            expectLine(helper, computer, "dropped\tfalse");
            expectLine(helper, computer, "sequencer\ttrue");
            if (!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(0, 3, 0))).is(Blocks.OAK_PLANKS))
                helper.fail("the planks should be back as a block");
            helper.succeed();
        });
    }

    // four screens are one pane of flip dots: a program lays markup out on it, sets a value, hears a button
    @GameTest(template = "empty5", timeoutTicks = 160)
    public static void screensShowMarkupAndHearButtons(GameTestHelper helper) {
        var computer = poweredComputer(helper);
        BlockState screen = ModBlocks.BRASS_SCREEN.getDefaultState().setValue(com.createnewera.block.HorizontalMachineBlock.FACING, Direction.NORTH);
        // facing north, the viewer's left is east: the block at x 1, y 3 is the pane's top left
        for (int x = 0; x < 2; x++)
            for (int y = 2; y < 4; y++)
                helper.setBlock(new BlockPos(x, y, 0), screen);
        String name = brassPort(helper, new BlockPos(0, 2, 0), Direction.SOUTH, 0);
        // the layout in a file of its own, the program only showing it
        computer.getFiles().write("farm.screen", String.join("\n",
                "<screen>",
                "  <title>FARM</title>",
                "  <row><label>IRON</label><value id='iron' align='right'>0</value></row>",
                "  <button id='go'>GO</button>",
                "</screen>"));
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local s = bus.get('" + name + "')",
                "local size = s.getSize()",
                "print('size', size.width, size.height)",
                "s.show('farm')",
                "s.set('iron', 42)",
                "print('get', s.get('iron'))",
                "print('bad', pcall(s.show, '<blink>no</blink>'))",
                "s.onClick('go', function(player) print('clicked go') end)"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(30, () -> {
            expectLine(helper, computer, "size\t56\t56");
            expectLine(helper, computer, "get\t42");
            if (computer.getConsole().stream().noneMatch(l -> l.text().startsWith("bad\tfalse") && l.text().contains("unknown tag <blink>")))
                helper.fail("a wrong tag should be refused, saying which: " + computer.getConsole());
            var pane = com.createnewera.block.screen.ScreenPane.of(helper.getLevel(), helper.absolutePos(new BlockPos(0, 2, 0)));
            if (!pane.origin().equals(helper.absolutePos(new BlockPos(1, 3, 0))))
                helper.fail("the pane's top left should be the east, upper screen, it's " + pane.origin());
            var controller = (com.createnewera.block.screen.BrassScreenBlockEntity) helper.getLevel().getBlockEntity(pane.origin());
            if (controller.shown() == null || controller.shown().get(0, 0) != com.createnewera.block.screen.ScreenPalette.AMBER)
                helper.fail("the title's band should be amber at the top left");
            // a click on the button: the lower east screen, a quarter in from its left and four pixels down
            BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 0));
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    new net.minecraft.world.phys.Vec3(lower.getX() + 0.25, lower.getY() + 0.75, lower.getZ()), Direction.NORTH, lower, false);
            helper.getLevel().getBlockState(lower).useWithoutItem(helper.getLevel(), helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE), hit);
        });
        helper.runAfterDelay(70, () -> {
            expectLine(helper, computer, "clicked go");
            // saving the file in the editor shows it again, keeping what the program set
            computer.handleAction(null, com.createnewera.network.EditorActionPayload.Action.SAVE, "farm.screen", String.join("\n",
                    "<screen color='lime'>",
                    "  <title>FARM 2</title>",
                    "  <row><label>IRON</label><value id='iron' align='right'>0</value></row>",
                    "</screen>"));
            var pane = com.createnewera.block.screen.ScreenPane.of(helper.getLevel(), helper.absolutePos(new BlockPos(0, 2, 0)));
            var controller = (com.createnewera.block.screen.BrassScreenBlockEntity) helper.getLevel().getBlockEntity(pane.origin());
            try {
                if (!"42".equals(controller.get("iron")))
                    helper.fail("the value the program set should survive saving the file, got " + controller.get("iron"));
            } catch (com.createnewera.block.screen.ScreenMarkup.MarkupException e) {
                helper.fail(e.getMessage());
            }
            if (controller.shown().get(0, 0) != com.createnewera.block.screen.ScreenPalette.of("lime"))
                helper.fail("the saved file's lime title should show");
            helper.succeed();
        });
    }

    // the physics assembler makes a ship when a program says so, after the tick, as its lever does
    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void programsBuildShips(GameTestHelper helper) {
        var assembler = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler"));
        if (assembler == Blocks.AIR) {
            helper.succeed();
            return;
        }
        var computer = poweredComputer(helper);
        // on the floor of a plank: it builds the ship from the block it's stuck to
        BlockPos at = new BlockPos(0, 2, 0);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.OAK_PLANKS);
        BlockState state = assembler.defaultBlockState();
        for (var property : state.getProperties())
            if (property.getName().equals("face") && property instanceof net.minecraft.world.level.block.state.properties.EnumProperty<?> face)
                state = state.setValue((net.minecraft.world.level.block.state.properties.EnumProperty) face,
                        net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
        helper.setBlock(at, state);
        String a = brassPort(helper, at, Direction.NORTH, 0);
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local a = bus.get('" + a + "')",
                "print('before', a.isAssembled())",
                "a.assemble()",
                "sleep(0.1)",
                "print('error', a.getError())"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(40, () -> {
            expectLine(helper, computer, "before\tfalse");
            if (helper.getLevel().getBlockState(helper.absolutePos(at)).is(assembler))
                helper.fail("the assembler should have gone into a ship: " + computer.getConsole());
            helper.succeed();
        });
    }

    // Aeronautics' controls from a program: throttle, wheel, optical sensor
    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void shipControlsTakeOrders(GameTestHelper helper) {
        java.util.function.Function<String, net.minecraft.world.level.block.Block> sim = id -> net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simulated", id));
        if (sim.apply("throttle_lever") == Blocks.AIR) {
            helper.succeed();
            return;
        }
        var computer = poweredComputer(helper);
        BlockPos throttle = new BlockPos(0, 1, 0), wheel = new BlockPos(4, 1, 0), optical = new BlockPos(0, 1, 4);
        helper.setBlock(throttle, sim.apply("throttle_lever"));
        helper.setBlock(wheel, sim.apply("steering_wheel"));
        helper.setBlock(optical, sim.apply("optical_sensor"));
        String t = brassPort(helper, throttle, Direction.DOWN, 0), w = brassPort(helper, wheel, Direction.DOWN, 1),
                o = brassPort(helper, optical, Direction.DOWN, 2);
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local t, w, o = bus.get('" + t + "'), bus.get('" + w + "'), bus.get('" + o + "')",
                "t.setValue(7)",
                "w.setAngle(30)",
                "o.setRange(12)",
                "sleep(0.2)",
                "print('throttle', t.getValue())",
                "print('wheel', w.getTargetAngle() <= 30, w.getMaxAngle() ~= nil)",
                "print('optical', o.getRange())"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(60, () -> {
            expectLine(helper, computer, "throttle\t7");
            expectLine(helper, computer, "wheel\ttrue\ttrue");
            expectLine(helper, computer, "optical\t12");
            helper.succeed();
        });
    }

    /** An input port on the block, cabled to the computer: something it gives orders to. */
    private static String input(GameTestHelper helper, BlockPos at, int socket) {
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(at);
        BusPort port = Bus.placePart(level, abs, Direction.UP, 6, 6, BusPort.Kind.PORT, PortMode.IN);
        if (port == null)
            helper.fail("no port on " + at);
        BusData data = BusData.get(level);
        data.connect(data.portsAt(helper.absolutePos(COMPUTER)).get(socket).id(), port.id());
        return data.nameOf(abs, level.getBlockState(abs).getBlock());
    }

    // Aeronautics' sensors give programs what they measure
    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void aeronauticsSensorsAreReadable(GameTestHelper helper) {
        var altitude = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simulated", "altitude_sensor"));
        var velocity = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simulated", "velocity_sensor"));
        if (altitude == Blocks.AIR) {
            helper.succeed();
            return;
        }
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(2, 1, 3), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(2, 1, 3));
        motor.generatedSpeed.setValue(256);
        helper.setBlock(new BlockPos(0, 1, 0), altitude);
        helper.setBlock(new BlockPos(4, 1, 0), velocity);
        String alt = device(helper, new BlockPos(0, 1, 0), 0), vel = device(helper, new BlockPos(4, 1, 0), 1);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        int y = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local a, v = bus.get('" + alt + "'), bus.get('" + vel + "')",
                "print('altitude', math.floor(a.getAltitude()), a.getAirPressure() > 0, v.getSpeed())"));
        computer.start("create_new_era.console.running");
        helper.succeedWhen(() -> {
            String line = "altitude\t" + y + "\ttrue\t0";
            if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals(line)))
                helper.fail("expected '" + line + "' in " + computer.getConsole());
        });
    }

    // the chat log hears chat, hands each message to onMessage, and keeps them
    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void chatLogsHearAndSpeak(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(2, 1, 3), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(2, 1, 3));
        motor.generatedSpeed.setValue(256);
        helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.CHAT_LOG.get());
        String log = device(helper, new BlockPos(0, 1, 0), 0);
        com.createnewera.chat.ChatLogBlockEntity entity = helper.getBlockEntity(new BlockPos(0, 1, 0));
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local log = bus.get('" + log + "')",
                "log.onMessage(function(m) print('heard', m.player, m.text, #log.getMessages()) log.say('hi ' .. m.player) end)",
                "print('ready')"));
        computer.start("create_new_era.console.running");
        helper.runAfterDelay(10, () -> entity.hear("Steve", "hola"));
        helper.succeedWhen(() -> {
            if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals("heard\tSteve\thola\t1")))
                helper.fail("expected the message in " + computer.getConsole());
        });
    }

    // Aeronautics' linked typewriter, reached by reflection: skipped when Aeronautics isn't installed
    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void typewritersAreReadable(GameTestHelper helper) {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simulated", "linked_typewriter"));
        if (block == Blocks.AIR) {
            helper.succeed();
            return;
        }
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(2, 1, 3), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(2, 1, 3));
        motor.generatedSpeed.setValue(256);
        helper.setBlock(new BlockPos(0, 1, 0), block);
        String typewriter = device(helper, new BlockPos(0, 1, 0), 0);
        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        // what the editor is sent about it has to fit its packet: a too-long list dropped players' connections
        BlockPos abs = helper.absolutePos(new BlockPos(0, 1, 0));
        var functions = new java.util.ArrayList<com.createnewera.network.NetworkDevice.Fn>();
        for (var fn : com.createnewera.bus.DeviceDrivers.describe(helper.getLevel(), abs, true, true))
            functions.add(new com.createnewera.network.NetworkDevice.Fn(fn.name(), fn.params(), fn.returns(), fn.doc(), fn.reads(), fn.choices()));
        var sent = new com.createnewera.network.NetworkDevice(typewriter, "simulated:linked_typewriter", "", abs, 2, true, true, functions);
        com.createnewera.network.NetworkDevice.STREAM_CODEC.encode(io.netty.buffer.Unpooled.buffer(), sent);

        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "local t = bus.get('" + typewriter + "')",
                "print('typewriter', #t.getPressedKeys(), t.isInUse(), t.isPressed('w'), t.getUser())",
                "t.onKeyDown(function(key) print('down', key) end)",
                "t.onKeyUp(function(key) print('up', key) end)",
                "t.onKey(function(key, down) if key == 'e' and not down then print('typed', t.getTyped()) end end)"));
        computer.start("create_new_era.console.running");
        // a tap within one tick, through the typewriter's own key handling: it mustn't be missed
        helper.runAfterDelay(10, () -> {
            var be = helper.getLevel().getBlockEntity(abs);
            try {
                be.getClass().getMethod("pressKey", int.class).invoke(be, 87); // W
                be.getClass().getMethod("releaseKey", int.class).invoke(be, 87);
                // a key with no frequency: Aeronautics doesn't send it, our client does, for whoever is typing
                var player = java.util.UUID.randomUUID();
                com.createnewera.bus.TypewriterEvents.typedBy(helper.getLevel(), abs, player, 69, true); // not typing on it yet: ignored
                // as checkAndStartUsing does, without the welcome message a mock player can't receive
                var user = be.getClass().getDeclaredField("currentUser");
                user.setAccessible(true);
                user.set(be, player);
                com.createnewera.bus.TypewriterEvents.typedBy(helper.getLevel(), abs, player, 69, true); // E
                com.createnewera.bus.TypewriterEvents.typedBy(helper.getLevel(), abs, player, 69, false);
            } catch (ReflectiveOperationException e) {
                helper.fail("couldn't press the typewriter's keys: " + e);
            }
        });
        helper.succeedWhen(() -> {
            for (String line : new String[] {"typewriter\t0\tfalse\tfalse\tnil", "down\tw", "up\tw", "down\te", "up\te", "typed\twe"})
                if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals(line)))
                    helper.fail("expected '" + line + "' in " + computer.getConsole());
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void blocksDoWhatProgramsAsk(GameTestHelper helper) {
        helper.setBlock(COMPUTER, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(2, 1, 3), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(2, 1, 3));
        motor.generatedSpeed.setValue(256);

        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
        ChestBlockEntity from = helper.getBlockEntity(new BlockPos(0, 1, 0));
        from.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
        helper.setBlock(new BlockPos(4, 1, 0), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 1, 4), Blocks.OAK_DOOR.defaultBlockState());
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.NOTE_BLOCK);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.OAK_SIGN);

        String a = device(helper, new BlockPos(0, 1, 0), 0), b = device(helper, new BlockPos(4, 1, 0), 1);
        String door = device(helper, new BlockPos(0, 1, 4), 2), note = device(helper, new BlockPos(4, 1, 4), 3);
        // the sign shares the note block's network through a second cable from its port
        BusData data = BusData.get(helper.getLevel());
        BusPort signPort = Bus.placePart(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), Direction.UP, 6, 6,
                BusPort.Kind.BRASS_PORT, PortMode.OUT);
        data.connect(data.portsAt(helper.absolutePos(new BlockPos(4, 1, 4))).get(0).id(), signPort.id());
        String sign = data.nameOf(helper.absolutePos(new BlockPos(4, 1, 2)), Blocks.OAK_SIGN);

        BrassComputerBlockEntity computer = helper.getBlockEntity(COMPUTER);
        computer.setProgram(ComputerFiles.STARTUP, String.join("\n",
                "print('moved', bus.get('" + a + "').pushItems('" + b + "', 5, 'minecraft:iron_ingot'))",
                "local d = bus.get('" + door + "') d.setOpen(true) print('open', d.isOpen(), d.getState().half)",
                "local s = bus.get('" + sign + "') s.setText('hola', 'mundo') print('line', s.getText()[2])",
                "local n = bus.get('" + note + "') n.setNote(12) print('note', n.getNote(), n.getType())"));
        computer.start("create_new_era.console.running");
        helper.succeedWhen(() -> {
            for (String line : new String[] {"moved\t5", "open\ttrue\tlower", "line\tmundo", "note\t12\tminecraft:note_block"})
                if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals(line)))
                    helper.fail("expected '" + line + "' in " + computer.getConsole());
            ChestBlockEntity to = helper.getBlockEntity(new BlockPos(4, 1, 0));
            if (to.getItem(0).getCount() != 5 || from.getItem(0).getCount() != 7)
                helper.fail("items didn't move: " + from.getItem(0) + " / " + to.getItem(0));
            if (!helper.getBlockState(new BlockPos(0, 2, 4)).getValue(BlockStateProperties.OPEN))
                helper.fail("the door's top half didn't open");
        });
    }
}
