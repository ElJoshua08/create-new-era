package com.createnewera.mixin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.Bus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * When Aeronautics builds a ship, Sable moves its blocks into a plot of their own (and back out
 * when it's taken apart), all through moveBlocks. Our ports and cables live in the bus's own
 * save, by position, so they'd stay behind on empty air: just before the blocks go, they're
 * moved the same way. Pseudo: applied only with Sable installed.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class SableAssemblyMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"), require = 0, remap = false)
    private static void createNewEra$moveBus(ServerLevel level, @Coerce Object transform, Iterable<BlockPos> blocks, CallbackInfo ci) {
        try {
            Class<?> type = transform.getClass();
            Method applyPos = type.getMethod("apply", BlockPos.class), applyVec = type.getMethod("apply", Vec3.class);
            Rotation rotation = (Rotation) type.getMethod("getRotation").invoke(transform);
            ServerLevel to = (ServerLevel) type.getMethod("getLevel").invoke(transform);
            Map<BlockPos, BlockPos> moves = new HashMap<>();
            for (BlockPos pos : blocks)
                moves.put(pos.immutable(), ((BlockPos) applyPos.invoke(transform, pos)).immutable());
            Bus.relocate(level, to == null ? level : to, moves, rotation, point -> {
                try {
                    return (Vec3) applyVec.invoke(transform, point);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            CreateNewEra.LOGGER.error("Couldn't move the bus along with blocks Sable moved", e);
        }
    }
}
