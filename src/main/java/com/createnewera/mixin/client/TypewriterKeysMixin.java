package com.createnewera.mixin.client;

import java.lang.ref.WeakReference;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.createnewera.client.TypewriterKeys;

/**
 * Aeronautics' typewriter only sends the keys bound to a frequency and lets every other key reach
 * the game (E opens the inventory mid-sentence). On a typewriter wired to the bus, those keys go
 * to the computer instead.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler", remap = false)
public abstract class TypewriterKeysMixin {
    @Shadow(remap = false)
    private static WeakReference<?> TYPEWRITER;

    @Inject(method = "onKeyPress(IIII)V", at = @At("HEAD"), require = 0, remap = false)
    private static void createNewEra$keyPress(int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        TypewriterKeys.key(TYPEWRITER == null ? null : TYPEWRITER.get(), key, scanCode, action);
    }
}
