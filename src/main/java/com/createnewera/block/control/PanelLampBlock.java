package com.createnewera.block.control;

import java.util.Locale;
import org.squiddev.cobalt.LuaBoolean;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaValue;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** An indicator a program lights: amber, green or red, or dark. */
public class PanelLampBlock extends PanelControlBlock {
    public enum Light implements StringRepresentable {
        OFF, AMBER, GREEN, RED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Light> LIGHT = EnumProperty.create("light", Light.class);
    /** How much light it gives off while lit, like a light block's level */
    public static final IntegerProperty BRIGHTNESS = IntegerProperty.create("brightness", 0, 15);

    public PanelLampBlock(Properties properties, DirectionalShape shape) {
        super(properties.lightLevel(s -> s.getValue(LIGHT) == Light.OFF ? 0 : s.getValue(BRIGHTNESS)), shape);
        registerDefaultState(defaultBlockState().setValue(LIGHT, Light.OFF).setValue(BRIGHTNESS, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIGHT, BRIGHTNESS);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("get", "", "\"off\" | \"amber\" | \"green\" | \"red\"", args -> level.getBlockState(pos).getValue(LIGHT).getSerializedName());
        api.order("set", "light: \"amber\" | \"green\" | \"red\" | \"off\" | boolean", "", args -> {
            Light light = parse(args.arg(1));
            BlockState state = level.getBlockState(pos);
            if (state.getValue(LIGHT) != light)
                level.setBlock(pos, state.setValue(LIGHT, light), Block.UPDATE_ALL);
            return null;
        });
        api.choices("set", java.util.List.of("amber", "green", "red", "off"));
        api.read("getBrightness", "", "number 0-15", args -> level.getBlockState(pos).getValue(BRIGHTNESS));
        api.order("setBrightness", "level: 0-15", "", args -> {
            BlockState state = level.getBlockState(pos);
            int brightness = net.minecraft.util.Mth.clamp(args.arg(1).checkInteger(), 0, 15);
            if (state.getValue(BRIGHTNESS) != brightness)
                level.setBlock(pos, state.setValue(BRIGHTNESS, brightness), Block.UPDATE_ALL);
            return null;
        });
    }

    /** "amber", "green", "red", "off", or true (amber) and false (off). */
    private static Light parse(LuaValue value) throws LuaError {
        if (value.isNil())
            return Light.OFF;
        if (value instanceof LuaBoolean flag)
            return flag.toBoolean() ? Light.AMBER : Light.OFF;
        String name = value.checkString().toLowerCase(Locale.ROOT);
        for (Light light : Light.values())
            if (light.getSerializedName().equals(name))
                return light;
        throw new LuaError("unknown light \"" + name + "\" (amber, green, red or off)");
    }

    @Override
    public Object value(BlockState state) {
        return state.getValue(LIGHT).getSerializedName();
    }

    @Override
    public String valueType() {
        return "\"off\" | \"amber\" | \"green\" | \"red\"";
    }

    @Override
    public Component goggleValue(BlockState state) {
        Component light = Component.translatable("create_new_era.goggles.light." + state.getValue(LIGHT).getSerializedName());
        return state.getValue(LIGHT) == Light.OFF ? light
                : light.copy().append(Component.translatable("create_new_era.goggles.brightness", state.getValue(BRIGHTNESS)));
    }
}
