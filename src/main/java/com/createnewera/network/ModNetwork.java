package com.createnewera.network;

import com.createnewera.client.ClientPayloads;
import com.createnewera.computer.BrassComputerBlockEntity;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModNetwork {
    public static void register(IEventBus bus) {
        bus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // lambdas, not method references: ClientPayloads must only load on the client
        registrar.playToClient(OpenEditorPayload.TYPE, OpenEditorPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.openEditor(payload));
        registrar.playToClient(ComputerStatePayload.TYPE, ComputerStatePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.computerState(payload));
        registrar.playToClient(BusPartsPayload.TYPE, BusPartsPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.busParts(payload));
        registrar.playToServer(EditorActionPayload.TYPE, EditorActionPayload.STREAM_CODEC, ModNetwork::editorAction);
    }

    private static void editorAction(EditorActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;
        var level = player.serverLevel();
        if (!level.isLoaded(payload.pos()) || !player.canInteractWithBlock(payload.pos(), 4))
            return;
        if (level.getBlockEntity(payload.pos()) instanceof BrassComputerBlockEntity computer)
            computer.handleAction(player, payload.action(), payload.name(), payload.argument());
    }

    private ModNetwork() {}
}
