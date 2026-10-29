package com.createnewera.client;

import com.createnewera.client.editor.ComputerEditorScreen;
import com.createnewera.client.bus.ClientBusParts;
import com.createnewera.network.BusPartsPayload;
import com.createnewera.network.ComputerStatePayload;
import com.createnewera.network.OpenEditorPayload;
import com.createnewera.network.SpeakPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-side handlers for server packets. Only ever loaded on the client. */
public final class ClientPayloads {
    public static void openEditor(OpenEditorPayload payload) {
        Minecraft.getInstance().setScreen(new ComputerEditorScreen(payload));
    }

    public static void computerState(ComputerStatePayload payload) {
        if (Minecraft.getInstance().screen instanceof ComputerEditorScreen editor && editor.getPos().equals(payload.pos()))
            editor.update(payload.snapshot());
    }

    public static void busParts(BusPartsPayload payload) {
        ClientBusParts.set(payload.chunk(), payload.parts(), payload.spans());
    }

    private static com.mojang.text2speech.Narrator voice;

    /**
     * A speaker spoke: said aloud with the system's text-to-speech voice when there is one, and
     * shown above the hotbar either way.
     */
    public static void speak(SpeakPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setOverlayMessage(Component.literal("\uD83D\uDD0A ").append(Component.literal(payload.text())), false);
        if (voice == null)
            voice = com.mojang.text2speech.Narrator.getNarrator();
        if (voice.active())
            voice.say(payload.text(), false);
    }

    private ClientPayloads() {}
}
