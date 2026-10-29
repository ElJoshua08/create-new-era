package com.createnewera.bus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.Varargs;

import com.createnewera.bus.DeviceDrivers.Api;
import com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Functions for particular blocks, on top of what turns, holds items or holds fluids: every
 * block's identity and state, moving items and fluids between devices, and what's special about
 * Create's and Minecraft's own blocks.
 */
final class BlockDrivers {
    /** Name, type, place and state: every device has these. */
    static void common(Api api, ServerLevel level, BlockPos pos) {
        api.family("block");
        api.read("getName", "", "string", args -> BusData.get(level).nameOf(pos, level.getBlockState(pos).getBlock()));
        api.read("getType", "", "block id", args -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
        api.read("getPosition", "", "{x, y, z}", args -> Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
        api.read("getState", "", "{[property] = value}", args -> state(level.getBlockState(pos)));
        api.read("getRedstone", "", "number 0-15", args -> level.getBestNeighborSignal(pos));
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            api.family("openable");
            api.read("isOpen", "", "boolean", args -> level.getBlockState(pos).getValue(BlockStateProperties.OPEN));
            api.order("setOpen", "open: boolean", "", args -> {
                setOpen(level, pos, args.arg(1).checkBoolean());
                return null;
            });
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            api.family("lit");
            api.read("isLit", "", "boolean", args -> level.getBlockState(pos).getValue(BlockStateProperties.LIT));
        }
    }

    /** A block's state as a table: each property's name with its value. */
    static Map<String, Object> state(BlockState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            Object value = state.getValue(property);
            out.put(property.getName(), value instanceof Boolean || value instanceof Integer ? value : nameOf(property, state));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String nameOf(Property property, BlockState state) {
        return property.getName((Comparable) state.getValue(property));
    }

    private static void setOpen(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(BlockStateProperties.OPEN) == open)
            return;
        if (state.getBlock() instanceof DoorBlock door)
            door.setOpen(null, level, state, pos, open); // both halves, with the door's own sound
        else
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
    }

    /** Moving items and fluids to and from other devices on the same network. */
    static void transfer(Api api, ServerLevel level, BlockPos pos) {
        IItemHandler items = DeviceDrivers.itemHandler(level, pos);
        if (items != null) {
            api.family("items");
            api.read("getSlot", "slot: number", "{name, count, max} | nil", args -> {
                int slot = args.arg(1).checkInteger() - 1;
                if (slot < 0 || slot >= items.getSlots())
                    throw new LuaError("bad argument #1 to 'getSlot' (slot 1-" + items.getSlots() + " expected, got " + (slot + 1) + ")");
                ItemStack stack = items.getStackInSlot(slot);
                return stack.isEmpty() ? null : Map.of("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        "count", stack.getCount(), "max", stack.getMaxStackSize());
            });
            api.order("pushItems", "to: device, count?: number, item?: id", "number moved",
                    args -> moveItems(level, pos, other(level, pos, args, "pushItems"), args, true));
            api.order("pullItems", "from: device, count?: number, item?: id", "number moved",
                    args -> moveItems(level, pos, other(level, pos, args, "pullItems"), args, false));
            api.choices("pushItems", List.of(DeviceDrivers.DEVICES));
            api.choices("pullItems", List.of(DeviceDrivers.DEVICES));
        }
        IFluidHandler fluids = DeviceDrivers.fluidHandler(level, pos);
        if (fluids != null) {
            api.family("fluids");
            api.order("pushFluid", "to: device, amount?: mB, fluid?: id", "number moved",
                    args -> moveFluid(level, pos, other(level, pos, args, "pushFluid"), args, true));
            api.order("pullFluid", "from: device, amount?: mB, fluid?: id", "number moved",
                    args -> moveFluid(level, pos, other(level, pos, args, "pullFluid"), args, false));
            api.choices("pushFluid", List.of(DeviceDrivers.DEVICES));
            api.choices("pullFluid", List.of(DeviceDrivers.DEVICES));
        }
    }

    /** The other device a transfer names: one the calling computer can reach too. */
    private static BlockPos other(ServerLevel level, BlockPos pos, Varargs args, String function) throws LuaError {
        String name = args.arg(1).checkString();
        BlockPos computer = DeviceDrivers.CALLER.get();
        BusDevice other = computer == null ? null : Bus.find(level, computer, name);
        if (other == null)
            throw new LuaError(function + ": no device named '" + name + "' on this computer's network");
        return other.pos();
    }

    private static int moveItems(ServerLevel level, BlockPos pos, BlockPos other, Varargs args, boolean push) throws LuaError {
        IItemHandler from = DeviceDrivers.itemHandler(level, push ? pos : other);
        IItemHandler to = DeviceDrivers.itemHandler(level, push ? other : pos);
        if (from == null || to == null)
            throw new LuaError("that device doesn't hold items");
        int left = args.arg(2).isNil() ? 64 : Math.max(0, args.arg(2).checkInteger());
        Item only = args.arg(3).isNil() ? null : BuiltInRegistries.ITEM.get(ResourceLocation.parse(args.arg(3).checkString()));
        int moved = 0;
        for (int slot = 0; slot < from.getSlots() && left > 0; slot++) {
            ItemStack inSlot = from.getStackInSlot(slot);
            if (inSlot.isEmpty() || (only != null && !inSlot.is(only)))
                continue;
            ItemStack taken = from.extractItem(slot, left, true);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(to, taken, true);
            int fits = taken.getCount() - rest.getCount();
            if (fits <= 0)
                continue;
            ItemStack really = from.extractItem(slot, fits, false);
            ItemHandlerHelper.insertItemStacked(to, really, false);
            moved += fits;
            left -= fits;
        }
        return moved;
    }

    private static int moveFluid(ServerLevel level, BlockPos pos, BlockPos other, Varargs args, boolean push) throws LuaError {
        IFluidHandler from = DeviceDrivers.fluidHandler(level, push ? pos : other);
        IFluidHandler to = DeviceDrivers.fluidHandler(level, push ? other : pos);
        if (from == null || to == null)
            throw new LuaError("that device doesn't hold fluids");
        int amount = args.arg(2).isNil() ? 1000 : Math.max(0, args.arg(2).checkInteger());
        FluidStack drained;
        if (args.arg(3).isNil()) {
            drained = from.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        } else {
            var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(args.arg(3).checkString()));
            drained = from.drain(new FluidStack(fluid, amount), IFluidHandler.FluidAction.SIMULATE);
        }
        if (drained.isEmpty())
            return 0;
        int fits = to.fill(drained, IFluidHandler.FluidAction.SIMULATE);
        if (fits <= 0)
            return 0;
        FluidStack really = from.drain(drained.copyWithAmount(fits), IFluidHandler.FluidAction.EXECUTE);
        return to.fill(really, IFluidHandler.FluidAction.EXECUTE);
    }

    /** What's special about particular blocks of Create's and Minecraft's. */
    static void special(Api api, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        // ---- Create ----
        if (be instanceof BlazeBurnerBlockEntity burner) {
            api.family("blaze_burner");
            api.read("getHeat", "", "\"none\" | \"smouldering\" | \"fading\" | \"kindled\" | \"seething\"",
                    args -> level.getBlockState(pos).getValue(BlazeBurnerBlock.HEAT_LEVEL).getSerializedName());
            api.read("getFuelTime", "", "ticks", args -> burner.getRemainingBurnTime());
        }
        if (be instanceof MechanicalPressBlockEntity press) {
            api.family("press");
            api.read("isPressing", "", "boolean", args -> press.getPressingBehaviour().running);
        }
        if (be instanceof MechanicalMixerBlockEntity mixer) {
            api.family("mixer");
            api.read("isMixing", "", "boolean", args -> mixer.running);
        }
        if (be instanceof GaugeBlockEntity gauge) {
            api.family("create_gauge");
            api.read("getDial", "", "number 0-1", args -> gauge.dialTarget);
        }
        if (be instanceof NixieTubeBlockEntity nixie) {
            api.family("create_nixie");
            api.read("getText", "", "string", args -> nixie.getFullText().getString());
            api.order("setText", "text: string", "", args -> {
                String text = args.arg(1).checkString();
                String json = Component.Serializer.toJson(Component.literal(text), level.registryAccess());
                // spread along the row of tubes this one is in, as Create's own display links do
                NixieTubeBlock.walkNixies(level, pos, true, (at, index) -> {
                    if (level.getBlockEntity(at) instanceof NixieTubeBlockEntity tube)
                        tube.displayCustomText(json, index);
                });
                return null;
            });
            api.order("clear", "", "", args -> {
                NixieTubeBlock.walkNixies(level, pos, true, (at, index) -> {
                    if (level.getBlockEntity(at) instanceof NixieTubeBlockEntity tube)
                        tube.clearCustomText();
                });
                return null;
            });
        }
        if (be instanceof StationBlockEntity stationBe) {
            api.family("station");
            api.read("getStationName", "", "string | nil", args -> {
                GlobalStation station = stationBe.getStation();
                return station == null ? null : station.name;
            });
            api.read("hasTrain", "", "boolean", args -> train(stationBe) != null);
            api.read("getTrainName", "", "string | nil", args -> {
                Train train = train(stationBe);
                return train == null ? null : train.name.getString();
            });
        }
        if (be instanceof FlapDisplayBlockEntity flapBe) {
            api.family("flap_display");
            api.read("getLineCount", "", "number", args -> controller(flapBe).getLines().size());
            api.read("getMaxChars", "", "number", args -> controller(flapBe).getMaxCharCount());
            api.order("setLine", "line: number, text: string", "", args -> {
                FlapDisplayBlockEntity board = controller(flapBe);
                int line = args.arg(1).checkInteger() - 1;
                if (line < 0 || line >= board.getLines().size())
                    throw new LuaError("bad argument #1 to 'setLine' (line 1-" + board.getLines().size() + " expected, got " + (line + 1) + ")");
                board.applyTextManually(line, Component.literal(args.arg(2).checkString()));
                return null;
            });
        }

        // ---- Minecraft ----
        if (be instanceof AbstractFurnaceBlockEntity furnace) {
            api.family("furnace");
            api.read("isBurning", "", "boolean", args -> level.getBlockState(pos).getValue(BlockStateProperties.LIT));
            api.read("getCookProgress", "", "number 0-1", args -> {
                var tag = furnace.saveWithoutMetadata(level.registryAccess());
                int total = tag.getShort("CookTimeTotal");
                return total == 0 ? 0.0 : tag.getShort("CookTime") / (double) total;
            });
            api.read("getBurnTime", "", "ticks", args -> (int) furnace.saveWithoutMetadata(level.registryAccess()).getShort("BurnTime"));
        }
        if (state.getBlock() instanceof NoteBlock) {
            api.family("note_block");
            api.read("getNote", "", "number 0-24", args -> level.getBlockState(pos).getValue(BlockStateProperties.NOTE));
            api.order("setNote", "note: 0-24", "", args -> {
                level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.NOTE, Mth.clamp(args.arg(1).checkInteger(), 0, 24)), Block.UPDATE_ALL);
                return null;
            });
            api.order("play", "", "", args -> {
                level.blockEvent(pos, level.getBlockState(pos).getBlock(), 0, 0);
                return null;
            });
        }
        if (state.getBlock() instanceof BellBlock bell) {
            api.family("bell");
            api.order("ring", "", "boolean", args -> bell.attemptToRing(level, pos, null));
        }
        if (be instanceof SignBlockEntity sign) {
            api.family("sign");
            api.read("getText", "", "{line 1, line 2, line 3, line 4}", args -> {
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < 4; i++)
                    lines.add(sign.getFrontText().getMessage(i, false).getString());
                return lines;
            });
            api.order("setText", "line1?, line2?, line3?, line4?", "", args -> {
                List<String> lines = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                    lines.add(args.arg(i).isNil() ? "" : args.arg(i).checkString());
                sign.updateText(text -> {
                    for (int i = 0; i < 4; i++)
                        text = text.setMessage(i, Component.literal(lines.get(i)));
                    return text;
                }, true);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                return null;
            });
        }
        if (be instanceof LecternBlockEntity lectern) {
            api.family("lectern");
            api.read("hasBook", "", "boolean", args -> lectern.hasBook());
            api.read("getPage", "", "number", args -> lectern.getPage() + 1);
        }
        if (be instanceof BeehiveBlockEntity hive) {
            api.family("beehive");
            api.read("getHoney", "", "number 0-5", args -> level.getBlockState(pos).getValue(BlockStateProperties.LEVEL_HONEY));
            api.read("getBees", "", "number", args -> hive.getOccupantCount());
        }
        if (state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER)) {
            api.family("composter");
            api.read("getFill", "", "number 0-8", args -> level.getBlockState(pos).getValue(BlockStateProperties.LEVEL_COMPOSTER));
        }
    }

    private static @Nullable Train train(StationBlockEntity station) {
        GlobalStation global = station.getStation();
        return global == null ? null : global.getPresentTrain();
    }

    private static FlapDisplayBlockEntity controller(FlapDisplayBlockEntity board) {
        FlapDisplayBlockEntity controller = board.getController();
        return controller == null ? board : controller;
    }

    private BlockDrivers() {}
}
