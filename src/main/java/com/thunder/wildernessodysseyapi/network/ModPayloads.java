package com.thunder.wildernessodysseyapi.network;

import com.thunder.wildernessodysseyapi.cloak.item.CloakState;
import com.thunder.wildernessodysseyapi.cloak.item.CloakTickHandler;
import com.thunder.wildernessodysseyapi.cloak.network.CloakInputPayload;
import com.thunder.wildernessodysseyapi.lorebook.CodexClientState;
import com.thunder.wildernessodysseyapi.lorebook.network.OpenCodexPayload;
import com.thunder.wildernessodysseyapi.lorebook.network.SyncLoreBookPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.SphSimulationSnapshotPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the custom payloads used by the cloak and lore-book systems.
 *
 * <p>Server-bound cloak input is treated as a request: authoritative state
 * changes still pass through server-owned cloak logic.</p>
 */
public final class ModPayloads {

    private static final String NETWORK_VERSION = "1";

    private ModPayloads() {
    }

    /**
     * Registers payload codecs and side-specific handlers during NeoForge network setup.
     *
     * @param event the payload registration event fired on the mod event bus
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);

        registrar.playToServer(CloakInputPayload.TYPE, CloakInputPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    CloakState.setHoldingBreath(serverPlayer, payload.holdingBreathDown());
                    if (payload.cloakTogglePressed()) {
                        CloakTickHandler.tryToggleCloak(serverPlayer);
                    }
                }));

        registrar.playToClient(SyncLoreBookPayload.TYPE, SyncLoreBookPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> CodexClientState.markCollected(payload.bookId())));
        registrar.playToClient(OpenCodexPayload.TYPE, OpenCodexPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (payload.open()) {
                        CodexClientState.requestOpen();
                    }
                }));

        registrar.playToClient(
                SphSimulationSnapshotPayload.TYPE,
                SphSimulationSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SPHSimulationManager.get().applyRemoteSnapshot(
                                payload.simulationId(),
                                context.player().level(),
                                payload.toParticles()
                        ))
        );
    }
}
