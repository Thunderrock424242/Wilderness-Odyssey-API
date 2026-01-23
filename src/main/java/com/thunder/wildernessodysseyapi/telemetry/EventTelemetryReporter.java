package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;

/**
 * Sends event-based telemetry payloads (server lifecycle and player login/logout).
 */
public final class EventTelemetryReporter {
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private EventTelemetryReporter() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        sendEvent("server_starting", null, event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        sendEvent("server_stopping", null, event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendEvent("player_login", player, player.server);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendEvent("player_logout", player, player.server);
        }
    }

    private static void sendEvent(String eventType, ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        EventTelemetryConfig.EventTelemetryValues config = EventTelemetryConfig.values();
        if (!config.enabled()) {
            return;
        }
        if (config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("event_type", eventType);
        payload.addProperty("timestamp", Instant.now().toString());
        payload.addProperty("mod_version", VERSION);
        payload.addProperty("server_online_players", server.getPlayerCount());
        payload.addProperty("server_max_players", server.getMaxPlayers());

        if (player != null && config.includePlayerIdentifiers()) {
            TelemetryConsentStore consentStore = TelemetryConsentStore.get(server);
            if (consentStore.getDecision(player.getUUID()) == ConsentDecision.ACCEPTED) {
                payload.addProperty("player_name", player.getGameProfile().getName());
                payload.addProperty("player_uuid", player.getUUID().toString());
            }
        }

        AsyncTaskManager.submitIoTask("event-telemetry", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.webhookUrl()))
                        .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    LOGGER.warn("[Telemetry] Event telemetry failed (status {}).", response.statusCode());
                }
            } catch (Exception ex) {
                LOGGER.warn("[Telemetry] Event telemetry failed: {}", ex.getMessage());
            }
            return Optional.empty();
        });
    }
}
