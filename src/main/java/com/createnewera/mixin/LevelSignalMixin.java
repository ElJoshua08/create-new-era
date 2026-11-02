package com.createnewera.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.createnewera.bus.PortSignals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;

/**
 * A data port with an input can power the block it's on, like a lever on its face: every block
 * asks the level what signal reaches it through each side, so the level adds the ports' own.
 * Level doesn't override getSignal; this gives it one that does that.
 */
@Mixin(Level.class)
public abstract class LevelSignalMixin implements SignalGetter {
    @Override
    public int getSignal(BlockPos pos, Direction direction) {
        int signal = SignalGetter.super.getSignal(pos, direction);
        return signal >= 15 ? signal : Math.max(signal, PortSignals.at((Level) (Object) this, pos, direction));
    }
}
