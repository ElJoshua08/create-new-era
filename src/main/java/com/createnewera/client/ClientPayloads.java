package com.createnewera.client;

import com.createnewera.client.editor.ComputerEditorScreen;
import com.createnewera.network.ComputerStatePayload;
import com.createnewera.network.OpenEditorPayload;

import net.minecraft.client.Minecraft;

/** Client-side handlers for server packets. Only ever loaded on the client. */
public final class ClientPayloads {
    public static void openEditor(OpenEditorPayload payload) {
        Minecraft.getInstance().setScreen(new ComputerEditorScreen(payload));
    }

    public static void computerState(ComputerStatePayload payload) {
        if (Minecraft.getInstance().screen instanceof ComputerEditorScreen editor && editor.getPos().equals(payload.pos()))
            editor.update(payload.snapshot());
    }

    private ClientPayloads() {}
}
