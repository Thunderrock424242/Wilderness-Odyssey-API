package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;

/**
 * Sends event-based telemetry payloads (server lifecycle and player login/logout).
 */
public final class EventTelemetryReporter {
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
        if (!TelemetryConfig.values().enabled() || !config.enabled()) {
            return;
        }
        if (config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            return;
        }
        if (!TelemetrySampling.shouldSample(eventType, config.sampleEveryNth(), config.sampleRatePercent())) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", TelemetryPayloads.SCHEMA_VERSION);
        payload.addProperty("event_type", eventType);
        payload.addProperty("timestamp", Instant.now().toString());
        payload.addProperty("mod_version", VERSION);
        payload.addProperty("server_online_players", server.getPlayerCount());
        payload.addProperty("server_max_players", server.getMaxPlayers());

        if (player != null && config.includePlayerIdentifiers()) {
            TelemetryConsentStore consentStore = TelemetryConsentStore.get(server);
            if (consentStore.getDecision(player.getUUID()) == ConsentDecision.ACCEPTED) {
                String playerName = player.getGameProfile().getName();
                String playerUuid = player.getUUID().toString();
                if (config.hashPlayerIdentifiers()) {
                    playerName = TelemetryHashing.hashIdentifier(playerName, config.identifierHashSalt());
                    playerUuid = TelemetryHashing.hashIdentifier(playerUuid, config.identifierHashSalt());
                }
                payload.addProperty("player_name", playerName);
                payload.addProperty("player_uuid", playerUuid);
                payload.addProperty("identifiers_hashed", config.hashPlayerIdentifiers());
            }
        }

        AsyncTaskManager.submitIoTask("event-telemetry", () -> {
            try {
                boolean sent = sendPayload(payload, config);
                if (!sent) {
                    enqueueFailedPayload(server, payload, config);
                }
            } catch (Exception ex) {
                LOGGER.warn("[Telemetry] Event telemetry failed: {}", ex.getMessage());
            }
            return Optional.empty();
        });
    }

    private static boolean sendPayload(JsonObject payload, EventTelemetryConfig.EventTelemetryValues config) {
        try {
            var response = TelemetryHttp.sendWithRetry(
                    TelemetryPayloads.buildRequest(config.webhookUrl(), config.requestTimeoutSeconds(), payload),
                    config.retryMaxAttempts(),
                    Duration.ofMillis(config.retryBaseDelayMs()),
                    Duration.ofMillis(config.retryMaxDelayMs())
            );
            if (response.statusCode() / 100 != 2) {
                LOGGER.warn("[Telemetry] Event telemetry failed (status {}).", response.statusCode());
                return false;
            }
            return true;
        } catch (Exception ex) {
            LOGGER.warn("[Telemetry] Event telemetry failed: {}", ex.getMessage());
            return false;
        }
    }

    private static void enqueueFailedPayload(net.minecraft.server.MinecraftServer server, JsonObject payload,
                                             EventTelemetryConfig.EventTelemetryValues config) {
        TelemetryConfig.TelemetryValues telemetryConfig = TelemetryConfig.values();
        TelemetryQueue.PendingTelemetryPayload pending = new TelemetryQueue.PendingTelemetryPayload(
                "event",
                payload,
                config.webhookUrl(),
                config.requestTimeoutSeconds(),
                config.retryMaxAttempts(),
                Duration.ofMillis(config.retryBaseDelayMs()),
                Duration.ofMillis(config.retryMaxDelayMs())
        );
        TelemetryQueue.get(server).enqueue(pending, telemetryConfig.queueMaxSize());
    }
}
