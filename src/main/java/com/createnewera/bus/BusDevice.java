package com.createnewera.bus;

import net.minecraft.core.BlockPos;

/**
 * A block on a computer's network, as that computer sees it.
 *
 * @param readable it has an output port on the network, so programs can read it
 * @param writable it has an input port, so programs can give it orders
 * @param distance in blocks, from the computer
 */
public record BusDevice(BlockPos pos, String name, String type, boolean readable, boolean writable, double distance) {}
