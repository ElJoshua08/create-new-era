package com.createnewera.chat;

import com.createnewera.block.HorizontalMachineBlock;
import com.createnewera.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A brass box with a paper roll that keeps the chat: programs read it and write to it. */
public class ChatLogBlock extends HorizontalMachineBlock implements EntityBlock {
    public ChatLogBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChatLogBlockEntity(ModBlockEntities.CHAT_LOG.get(), pos, state);
    }
}
