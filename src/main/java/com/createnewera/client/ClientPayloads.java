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
        else
            SPEECH.execute(() -> sayWithCommand(payload.text()));
    }

    /**
     * Linux has Minecraft's voice only with libflite installed; most desktops have espeak or
     * speech-dispatcher instead, so those speak when it can't. One line at a time, in order.
     */
    private static final java.util.concurrent.ExecutorService SPEECH = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Create: New Era speaker voice");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.List<String> VOICES = java.util.List.of("espeak-ng", "espeak", "spd-say");
    private static int voiceCommand = 0;

    private static void sayWithCommand(String text) {
        while (voiceCommand < VOICES.size()) {
            String command = VOICES.get(voiceCommand);
            java.util.List<String> args = command.equals("spd-say") ? java.util.List.of(command, "--wait", "--", text)
                    : java.util.List.of(command, "--", text);
            try {
                new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
                return;
            } catch (java.io.IOException e) {
                voiceCommand++; // not installed: try the next, and don't try this one again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private ClientPayloads() {}
}
