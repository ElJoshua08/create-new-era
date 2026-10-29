package com.createnewera.block.control;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A toggle lever: on or off until it's flipped again, by hand or by a program. */
public class PanelSwitchBlock extends PanelControlBlock {
    public static final BooleanProperty ON = BlockStateProperties.POWERED;

    public PanelSwitchBlock(Properties properties, DirectionalShape shape) {
        super(properties, shape);
        registerDefaultState(defaultBlockState().setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        set(level, pos, !state.getValue(ON));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void set(Level level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(ON) == on)
            return;
        level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, on ? 0.7f : 0.6f);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.read("isOn", "", "boolean", args -> level.getBlockState(pos).getValue(ON));
        api.order("setOn", "on: boolean", "", args -> {
            set(level, pos, args.arg(1).checkBoolean());
            return null;
        });
    }

    @Override
    public Object value(BlockState state) {
        return state.getValue(ON);
    }

    @Override
    public String valueType() {
        return "boolean";
    }

    @Override
    public Component goggleValue(BlockState state) {
        return Component.translatable(state.getValue(ON) ? "create_new_era.goggles.on" : "create_new_era.goggles.off");
    }
}
