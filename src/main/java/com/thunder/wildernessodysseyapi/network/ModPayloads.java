package com.thunder.wildernessodysseyapi.network;

import com.thunder.wildernessodysseyapi.cloak.item.CloakState;
import com.thunder.wildernessodysseyapi.cloak.item.CloakTickHandler;
import com.thunder.wildernessodysseyapi.cloak.network.CloakInputPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioServerService;
import com.thunder.wildernessodysseyapi.developmentstudio.client.StudioClientState;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioRequestPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioBookmarkActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioCampusUpgradePayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioLocationTeleportPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioStructureActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEntityActionPayload;
import com.thunder.wildernessodysseyapi.dataengine.network.DataEnginePayloads;
import com.thunder.wildernessodysseyapi.ecosystem.client.EnvironmentalMemoryClientState;
import com.thunder.wildernessodysseyapi.ecosystem.distant.client.ClientDistantWildlifeState;
import com.thunder.wildernessodysseyapi.ecosystem.distant.network.DistantWildlifeSyncPayload;
import com.thunder.wildernessodysseyapi.ecosystem.network.EnvironmentalMemoryDebugPayload;
import com.thunder.wildernessodysseyapi.environment.client.ClientEnvironmentState;
import com.thunder.wildernessodysseyapi.environment.network.EnvironmentSyncPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.structure.StudioStructureService;
import com.thunder.wildernessodysseyapi.developmentstudio.entity.StudioEntityService;
import com.thunder.wildernessodysseyapi.developmentstudio.environment.StudioEnvironmentService;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload;
import com.thunder.wildernessodysseyapi.lorebook.CodexClientState;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.lorebook.network.OpenCodexPayload;
import com.thunder.wildernessodysseyapi.lorebook.network.SaveCodexJournalPayload;
import com.thunder.wildernessodysseyapi.lorebook.network.SyncCodexJournalPayload;
import com.thunder.wildernessodysseyapi.lorebook.network.SyncLoreBookPayload;
import com.thunder.wildernessodysseyapi.vegetation.client.ClientVegetationClimateStore;
import com.thunder.wildernessodysseyapi.vegetation.network.ReactiveVegetationSyncPayload;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.network.OceanSeaStatePayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.SphLocalEffectPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.SphSimulationSnapshotPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.WaterVolumeChunkPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.WaterVolumeDeltaPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWatershedSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.network.WatershedRegionSyncPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.client.audio.DistantThunderAudioManager;
import com.thunder.wildernessodysseyapi.weather.networking.DistantThunderSystemSyncPayload;
import com.thunder.wildernessodysseyapi.weather.networking.WeatherRegionSyncPayload;
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

    private static final String NETWORK_VERSION = "21";

    private ModPayloads() {
    }

    /**
     * Registers payload codecs and side-specific handlers during NeoForge network setup.
     *
     * @param event the payload registration event fired on the mod event bus
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        DataEnginePayloads.register(registrar);

        // PayloadRegistrar already marshals play handlers to the main thread.
        // Handle this dimension-sensitive state immediately so a second queue
        // hop cannot carry it across a client dimension transition.
        registrar.playToClient(
                ReactiveVegetationSyncPayload.TYPE,
                ReactiveVegetationSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientVegetationClimateStore.accept(context.player().level(), payload)
        );

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
        registrar.playToClient(SyncCodexJournalPayload.TYPE, SyncCodexJournalPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> CodexClientState.syncJournal(payload.text())));
        registrar.playToClient(OpenCodexPayload.TYPE, OpenCodexPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (payload.open()) {
                        CodexClientState.requestOpen();
                    }
                }));
        registrar.playToServer(SaveCodexJournalPayload.TYPE, SaveCodexJournalPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        LoreBookManager.saveJournalText(serverPlayer, payload.text());
                    }
                }));

        // Studio payloads are requests only. Every handler rechecks world scope,
        // player authority, registered ids, and bounded server-owned state.
        registrar.playToServer(OpenStudioRequestPayload.TYPE, OpenStudioRequestPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioServerService.open(serverPlayer);
                    }
                }));
        registrar.playToServer(StudioBookmarkActionPayload.TYPE, StudioBookmarkActionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioServerService.handleBookmarkAction(serverPlayer, payload);
                    }
                }));
        registrar.playToServer(StudioCampusUpgradePayload.TYPE, StudioCampusUpgradePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioServerService.handleCampusUpgrade(serverPlayer, payload);
                    }
                }));
        registrar.playToServer(StudioLocationTeleportPayload.TYPE, StudioLocationTeleportPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioServerService.teleportToCampusLocation(serverPlayer, payload.locationId());
                    }
                }));
        registrar.playToServer(StudioStructureActionPayload.TYPE, StudioStructureActionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioStructureService.handle(serverPlayer, payload);
                    }
                }));
        registrar.playToServer(StudioEntityActionPayload.TYPE, StudioEntityActionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioEntityService.handle(serverPlayer, payload);
                    }
                }));
        registrar.playToServer(StudioEnvironmentActionPayload.TYPE, StudioEnvironmentActionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        StudioEnvironmentService.handle(serverPlayer, payload);
                    }
                }));
        registrar.playToClient(OpenStudioPayload.TYPE, OpenStudioPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> StudioClientState.accept(payload)));

        registrar.playToClient(
                EnvironmentalMemoryDebugPayload.TYPE,
                EnvironmentalMemoryDebugPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        EnvironmentalMemoryClientState.accept(payload))
        );
        registrar.playToClient(
                EnvironmentSyncPayload.TYPE,
                EnvironmentSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
                        ClientEnvironmentState.accept(level, payload);
                    }
                })
        );

        // These coordinate-only water payloads must resolve the active level on
        // the registrar's existing main-thread handoff. A second enqueue could
        // otherwise apply an old-dimension packet after a respawn transition.
        registrar.playToClient(
                SphSimulationSnapshotPayload.TYPE,
                SphSimulationSnapshotPayload.STREAM_CODEC,
                (payload, context) -> SPHSimulationManager.get().applyRemoteSnapshot(
                        payload.simulationId(),
                        context.player().level(),
                        payload.toParticles()
                )
        );
        registrar.playToClient(
                SphLocalEffectPayload.TYPE,
                SphLocalEffectPayload.STREAM_CODEC,
                (payload, context) -> payload.spawnClientEffect(context.player().level())
        );
        registrar.playToClient(
                WaterVolumeChunkPayload.TYPE,
                WaterVolumeChunkPayload.STREAM_CODEC,
                (payload, context) -> payload.apply(context.player().level())
        );
        registrar.playToClient(
                WaterVolumeDeltaPayload.TYPE,
                WaterVolumeDeltaPayload.STREAM_CODEC,
                (payload, context) -> payload.apply(context.player().level())
        );
        registrar.playToClient(
                OceanSeaStatePayload.TYPE,
                OceanSeaStatePayload.STREAM_CODEC,
                (payload, context) -> ClientOceanSeaState.accept(context.player().level(), payload)
        );
        registrar.playToClient(
                WatershedRegionSyncPayload.TYPE,
                WatershedRegionSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientWatershedSnapshotStore.accept(context.player().level(), payload)
        );
        registrar.playToClient(
                WeatherRegionSyncPayload.TYPE,
                WeatherRegionSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientWeatherCoordinator.accept(payload))
        );
        registrar.playToClient(
                DistantThunderSystemSyncPayload.TYPE,
                DistantThunderSystemSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        DistantThunderAudioManager.accept(payload))
        );
        registrar.playToClient(
                DistantWildlifeSyncPayload.TYPE,
                DistantWildlifeSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientDistantWildlifeState.accept(payload))
        );
    }
}
