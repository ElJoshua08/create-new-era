package com.createnewera.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.createnewera.bus.TypewriterEvents;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Hears every key pressed and released on Aeronautics' linked typewriter, as its own
 * ComputerCraft support does, so programs on the bus get each one: polling which keys are held
 * once a tick misses quick taps. Pseudo: applied only when Aeronautics (its Simulated core) is
 * installed.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity", remap = false)
public abstract class TypewriterMixin {
    @Inject(method = "pressKey(I)V", at = @At("HEAD"), require = 0, remap = false)
    private void createNewEra$pressed(int key, CallbackInfo ci) {
        TypewriterEvents.key((BlockEntity) (Object) this, key, true);
    }

    @Inject(method = "releaseKey(I)V", at = @At("HEAD"), require = 0, remap = false)
    private void createNewEra$released(int key, CallbackInfo ci) {
        TypewriterEvents.key((BlockEntity) (Object) this, key, false);
    }
}
