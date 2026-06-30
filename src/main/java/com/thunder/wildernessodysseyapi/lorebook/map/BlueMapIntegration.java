package com.thunder.wildernessodysseyapi.lorebook.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Optional bridge that mirrors Field Codex POIs into BlueMap's web marker API.
 *
 * <p>This class is only loaded after the main mod confirms that BlueMap is
 * installed. Keeping all direct BlueMap API imports here lets Wilderness
 * Odyssey keep BlueMap as an optional compile-time dependency instead of a hard
 * runtime requirement.</p>
 */
public final class BlueMapIntegration {
    private static final String MARKER_SET_ID = "wilderness_odyssey";
    private static final String MARKER_SET_LABEL = "Wilderness Odyssey";
    private static final double MARKER_MAX_DISTANCE = 10_000_000.0D;
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean();

    private static volatile BlueMapAPI activeApi;
    private static volatile MinecraftServer activeServer;

    private BlueMapIntegration() {
    }

    /**
     * Registers BlueMap callbacks and server lifecycle hooks once per JVM.
     *
     * <p>BlueMap can enable before or after the server finishes loading worlds,
     * so the bridge waits until both the API and the server are available.</p>
     */
    public static void bootstrap() {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }

        BlueMapAPI.onEnable(api -> {
            activeApi = api;
            publishIfReady();
        });
        BlueMapAPI.onDisable(api -> {
            if (activeApi == api) {
                activeApi = null;
            }
        });
        NeoForge.EVENT_BUS.register(BlueMapIntegration.class);
        LOGGER.info("BlueMap detected; Wilderness Odyssey map markers will be published when the server is ready");
    }

    /** Schedules a BlueMap marker refresh if BlueMap and the server are ready. */
    public static void publishIfReady() {
        BlueMapAPI api = activeApi;
        MinecraftServer server = activeServer;
        if (api == null || server == null || !CodexMapServerConfig.PUBLISH_BLUEMAP_MARKERS.get()) {
            return;
        }

        // Marker data is sourced from Minecraft saved data, so collect it on the server thread.
        server.execute(() -> publish(api, server));
    }

    /**
     * Captures the loaded server once dimensions are available.
     *
     * @param event NeoForge server-started event fired after worlds are loaded
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        activeServer = event.getServer();
        publishIfReady();
    }

    /**
     * Drops the server reference before level data is torn down.
     *
     * @param event NeoForge server-stopping event
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        activeServer = null;
    }

    private static void publish(BlueMapAPI api, MinecraftServer server) {
        List<CodexMapPoi> pois = CodexMapPoiProvider.collectPois(server);
        String configuredMapId = CodexMapServerConfig.MAP_ID.get().trim();
        int publishedMarkers = 0;
        int touchedMaps = 0;

        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimension = level.dimension().location();
            List<CodexMapPoi> levelPois = pois.stream()
                    .filter(poi -> poi.dimension().equals(dimension))
                    .toList();

            for (BlueMapMap map : findMaps(api, level, configuredMapId)) {
                publishToMap(map, levelPois);
                publishedMarkers += levelPois.size();
                touchedMaps++;
            }
        }

        LOGGER.debug("Published {} Wilderness Odyssey POIs to {} BlueMap map(s)", publishedMarkers, touchedMaps);
    }

    private static Collection<BlueMapMap> findMaps(BlueMapAPI api, ServerLevel level, String configuredMapId) {
        return api.getWorld(level)
                .map(BlueMapWorld::getMaps)
                .stream()
                .flatMap(Collection::stream)
                .filter(map -> configuredMapId.isBlank() || map.getId().equals(configuredMapId))
                .toList();
    }

    private static void publishToMap(BlueMapMap map, List<CodexMapPoi> pois) {
        MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(MARKER_SET_ID, ignored ->
                MarkerSet.builder()
                        .label(MARKER_SET_LABEL)
                        .toggleable(true)
                        .defaultHidden(false)
                        .sorting(30)
                        .build());
        markerSet.getMarkers().clear();

        for (CodexMapPoi poi : pois) {
            markerSet.put(poi.id(), POIMarker.builder()
                    .label(poi.label())
                    .position(poi.x() + 0.5D, poi.y() + 0.5D, poi.z() + 0.5D)
                    .detail(detailText(poi))
                    .maxDistance(MARKER_MAX_DISTANCE)
                    .build());
        }
    }

    private static String detailText(CodexMapPoi poi) {
        return "%s at %d, %d, %d".formatted(poi.type(), poi.x(), poi.y(), poi.z());
    }
}
