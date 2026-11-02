package com.createnewera.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.createnewera.block.control.PanelLampBlock;
import com.createnewera.registry.ModBlocks;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shader packs give blocks their light by the ids in their block.properties, and list no modded
 * blocks, so a lit panel lamp glowed far less than a torch. As Iris reads a pack's ids, a lit
 * lamp gets the torch's: packs light a torch's brightest pixels hard (its flame), which on the
 * lamp are its lit glass, not its plate. Pseudo: applied only with Iris installed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping", remap = false)
public abstract class IrisBlockIdsMixin {
    @Inject(method = "createBlockStateIdMap", at = @At("RETURN"), require = 0, remap = false)
    private static void createNewEra$lampsAsTorches(CallbackInfoReturnable<Object2IntMap<BlockState>> cir) {
        Object2IntMap<BlockState> ids = cir.getReturnValue();
        if (ids == null || !ids.containsKey(Blocks.TORCH.defaultBlockState()))
            return;
        int torch = ids.getInt(Blocks.TORCH.defaultBlockState());
        for (BlockState state : ModBlocks.PANEL_LAMP.get().getStateDefinition().getPossibleStates())
            if (state.getValue(PanelLampBlock.LIGHT) != PanelLampBlock.Light.OFF)
                ids.put(state, torch);
    }
}
