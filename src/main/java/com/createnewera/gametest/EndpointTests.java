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
                "t.onKeyDown(function(key) print('down', key) end)"));
        computer.start("create_new_era.console.running");
        helper.succeedWhen(() -> {
            if (computer.getConsole().stream().noneMatch(l -> l.kind() == ConsoleLine.Kind.OUTPUT && l.text().equals("typewriter\t0\tfalse\tfalse\tnil")))
                helper.fail("expected the typewriter's state in " + computer.getConsole());
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
