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
 * lamp gets those of a vanilla light whose rules light only its glass, not its plate: a torch's
 * for amber and red (packs light its full-red pixels hard: the flame), an end rod's for green
 * (they light its brightest pixels). The lit glass is drawn to clear those (gen_textures.py).
 * Pseudo: applied only with Iris installed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping", remap = false)
public abstract class IrisBlockIdsMixin {
    @Inject(method = "createBlockStateIdMap", at = @At("RETURN"), require = 0, remap = false)
    private static void createNewEra$lampsAsTorches(CallbackInfoReturnable<Object2IntMap<BlockState>> cir) {
        Object2IntMap<BlockState> ids = cir.getReturnValue();
        if (ids == null)
            return;
        BlockState torch = Blocks.TORCH.defaultBlockState(), endRod = Blocks.END_ROD.defaultBlockState();
        for (BlockState state : ModBlocks.PANEL_LAMP.get().getStateDefinition().getPossibleStates()) {
            BlockState like = switch (state.getValue(PanelLampBlock.LIGHT)) {
                case AMBER, RED -> torch;
                case GREEN -> endRod;
                case OFF -> null;
            };
            if (like != null && ids.containsKey(like))
                ids.put(state, ids.getInt(like));
        }
    }
}
