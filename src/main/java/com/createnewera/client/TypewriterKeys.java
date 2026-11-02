package com.createnewera.client;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.createnewera.client.bus.ClientBusParts;
import com.createnewera.network.TypewriterKeyPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** The client half of TypewriterKeysMixin: forwards a typewriter's unbound keys to the server. */
public final class TypewriterKeys {
    private static final String HANDLER = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler";
    private static @Nullable Method getMode, preventPress, getEntries, getEntry;
    private static boolean broken;

    public static void key(@Nullable Object typewriter, int key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (broken || !(typewriter instanceof BlockEntity be) || be.isRemoved() || mc.screen != null
                || key == GLFW.GLFW_KEY_ESCAPE || action == GLFW.GLFW_REPEAT || ClientBusParts.at(be.getBlockPos()).isEmpty())
            return;
        try {
            if (getMode == null) {
                Class<?> handler = Class.forName(HANDLER, false, TypewriterKeys.class.getClassLoader());
                getMode = handler.getMethod("getMode");
                preventPress = handler.getMethod("preventPress", int.class, int.class);
                getEntries = be.getClass().getMethod("getTypewriterEntries");
            }
            if (!String.valueOf(getMode.invoke(null)).equals("ACTIVE"))
                return;
            Object entries = getEntries.invoke(be);
            if (getEntry == null)
                getEntry = entries.getClass().getMethod("getEntry", int.class);
            // bound keys already reach the typewriter, and through it the computer
            if (getEntry.invoke(entries, key) != null)
                return;
            PacketDistributor.sendToServer(new TypewriterKeyPayload(be.getBlockPos(), key, action == GLFW.GLFW_PRESS));
            preventPress.invoke(null, key, scanCode);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true; // a different Aeronautics: leave its keys alone rather than fail every press
        }
    }

    private TypewriterKeys() {}
}
