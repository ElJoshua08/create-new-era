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

    public PanelLampBlock(Properties properties, DirectionalShape shape) {
        super(properties.lightLevel(s -> s.getValue(LIGHT) == Light.OFF ? 0 : 9), shape);
        registerDefaultState(defaultBlockState().setValue(LIGHT, Light.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIGHT);
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
        return Component.translatable("create_new_era.goggles.light." + state.getValue(LIGHT).getSerializedName());
    }
}
