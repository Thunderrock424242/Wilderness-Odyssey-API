package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * Collects player location and account age data and sends it to a Google Sheets webhook.
 */
public final class PlayerTelemetryReporter {
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private PlayerTelemetryReporter() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerTelemetryConfig.TelemetryConfigValues config = PlayerTelemetryConfig.values();
        if (!config.enabled()) {
            return;
        }

        if (config.sheetWebhookUrl() == null || config.sheetWebhookUrl().isBlank()) {
            LOGGER.warn("[Telemetry] Telemetry enabled but sheetWebhookUrl is blank. Skipping export.");
            return;
        }

        TelemetryConsentStore consentStore = TelemetryConsentStore.get(player.server);
        if (consentStore.getDecision(player.getUUID()) != ConsentDecision.ACCEPTED) {
            return;
        }

        String ipAddress = resolveIpAddress(player);
        AsyncTaskManager.submitIoTask("player-telemetry-export", () -> {
            try {
                GeoInfo geoInfo = fetchGeoInfo(ipAddress, config);
                AccountAgeInfo accountAge = fetchAccountAge(player.getUUID(), config);
                postToSheet(player, geoInfo, accountAge, config.sheetWebhookUrl(), config.requestTimeoutSeconds());
            } catch (Exception ex) {
                LOGGER.error("[Telemetry] Failed to export telemetry for {}", player.getGameProfile().getName(), ex);
            }
            return Optional.empty();
        });
    }

    private static String resolveIpAddress(ServerPlayer player) {
        if (player.connection == null || player.connection.getConnection() == null) {
            return null;
        }
        SocketAddress address = player.connection.getConnection().getRemoteAddress();
        if (address instanceof InetSocketAddress inetSocketAddress) {
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress().getHostAddress();
            }
        }
        return null;
    }

    private static GeoInfo fetchGeoInfo(String ipAddress, PlayerTelemetryConfig.TelemetryConfigValues config) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return GeoInfo.empty();
        }
        String endpointTemplate = config.geoIpEndpoint();
        if (endpointTemplate == null || endpointTemplate.isBlank()) {
            return GeoInfo.empty();
        }
        String endpoint = endpointTemplate.replace("{ip}", ipAddress.trim());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOGGER.warn("[Telemetry] Geo IP lookup failed (status {}).", response.statusCode());
                return GeoInfo.empty();
            }
            JsonElement element = JsonParser.parseString(response.body());
            if (!element.isJsonObject()) {
                return GeoInfo.empty();
            }
            JsonObject object = element.getAsJsonObject();
            String state = firstNonBlank(object, "region", "regionName", "state", "region_name");
            String country = firstNonBlank(object, "country", "country_name", "countryName", "country_code", "countryCode");
            return new GeoInfo(state, country);
        } catch (Exception ex) {
            LOGGER.warn("[Telemetry] Geo IP lookup failed: {}", ex.getMessage());
            return GeoInfo.empty();
        }
    }

    private static AccountAgeInfo fetchAccountAge(UUID uuid, PlayerTelemetryConfig.TelemetryConfigValues config) {
        String endpointTemplate = config.accountAgeEndpoint();
        if (uuid == null || endpointTemplate == null || endpointTemplate.isBlank()) {
            return AccountAgeInfo.empty();
        }
        String compactUuid = uuid.toString().replace("-", "");
        String endpoint = endpointTemplate.replace("{uuid}", compactUuid);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOGGER.warn("[Telemetry] Account age lookup failed (status {}).", response.statusCode());
                return AccountAgeInfo.empty();
            }
            JsonElement element = JsonParser.parseString(response.body());
            if (!element.isJsonArray()) {
                return AccountAgeInfo.empty();
            }
            JsonArray array = element.getAsJsonArray();
            Long earliestChange = null;
            for (JsonElement entry : array) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject obj = entry.getAsJsonObject();
                if (obj.has("changedToAt") && obj.get("changedToAt").isJsonPrimitive()) {
                    long changedToAt = obj.get("changedToAt").getAsLong();
                    if (changedToAt > 0 && (earliestChange == null || changedToAt < earliestChange)) {
                        earliestChange = changedToAt;
                    }
                }
            }
            if (earliestChange == null) {
                return AccountAgeInfo.empty();
            }
            Instant firstChange = Instant.ofEpochMilli(earliestChange);
            long ageDays = Duration.between(firstChange, Instant.now()).toDays();
            return new AccountAgeInfo(ageDays, firstChange);
        } catch (Exception ex) {
            LOGGER.warn("[Telemetry] Account age lookup failed: {}", ex.getMessage());
            return AccountAgeInfo.empty();
        }
    }

    private static void postToSheet(ServerPlayer player, GeoInfo geoInfo, AccountAgeInfo accountAge, String webhookUrl, int timeoutSeconds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", player.getUUID().toString());
        payload.addProperty("player_name", player.getGameProfile().getName());
        payload.addProperty("timestamp", Instant.now().toString());

        if (geoInfo.state != null) {
            payload.addProperty("state", geoInfo.state);
        }
        if (geoInfo.country != null) {
            payload.addProperty("country", geoInfo.country);
        }

        if (accountAge.estimatedAgeDays != null) {
            payload.addProperty("account_age_days", accountAge.estimatedAgeDays);
            payload.addProperty("account_age_reference", accountAge.referenceDate.toString());
        } else {
            payload.addProperty("account_age_reference", "unknown");
        }
        payload.addProperty("account_age_source", accountAge.source);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOGGER.warn("[Telemetry] Sheet export failed (status {}).", response.statusCode());
            } else {
                LOGGER.info("[Telemetry] Exported telemetry for {}.", player.getGameProfile().getName());
            }
        } catch (Exception ex) {
            LOGGER.warn("[Telemetry] Sheet export failed: {}", ex.getMessage());
        }
    }

    private static String firstNonBlank(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                String value = object.get(key).getAsString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private record GeoInfo(String state, String country) {
        static GeoInfo empty() {
            return new GeoInfo(null, null);
        }
    }

    private record AccountAgeInfo(Long estimatedAgeDays, Instant referenceDate, String source) {
        static AccountAgeInfo empty() {
            return new AccountAgeInfo(null, null, "unknown");
        }

        AccountAgeInfo(Long estimatedAgeDays, Instant referenceDate) {
            this(estimatedAgeDays, referenceDate, "name_change");
        }
    }
}
