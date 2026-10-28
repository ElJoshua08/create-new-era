package com.createnewera.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a cable being run starts: the part, the point it plugs in at (for drawing the cable
 * that follows you to the second click), and the dimension it's in.
 */
public record CableStart(long port, Vec3 anchor, ResourceKey<Level> dimension) {
    public static final Codec<CableStart> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("port").forGetter(CableStart::port),
            Vec3.CODEC.fieldOf("anchor").forGetter(CableStart::anchor),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(CableStart::dimension)
    ).apply(i, CableStart::new));

    public static final StreamCodec<ByteBuf, CableStart> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
