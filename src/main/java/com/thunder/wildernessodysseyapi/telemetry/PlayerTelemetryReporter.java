package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * Collects player country and account age data and sends it to a Google Sheets webhook.
 */
public final class PlayerTelemetryReporter {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, CachedGeoInfo> GEO_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedAccountAge> ACCOUNT_AGE_CACHE = new ConcurrentHashMap<>();

    private PlayerTelemetryReporter() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.server.isDedicatedServer()) {
            return;
        }

        PlayerTelemetryConfig.TelemetryConfigValues config = PlayerTelemetryConfig.values();
        if (!TelemetryConfig.values().enabled() || !config.enabled()) {
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

        if (!TelemetrySampling.shouldSample("player_login", config.sampleEveryNth(), config.sampleRatePercent())) {
            return;
        }

        String ipAddress = resolveIpAddress(player);
        long playTimeSeconds = getTotalPlayTimeSeconds(player);
        AsyncTaskManager.submitIoTask("player-telemetry-export", () -> {
            try {
                GeoInfo geoInfo = resolveGeoInfo(player.getUUID(), ipAddress, config);
                AccountAgeInfo accountAge = resolveAccountAge(player.getUUID(), config);
                JsonObject payload = buildPayload(player, geoInfo, accountAge, playTimeSeconds, null, "login", config);
                boolean sent = sendPayload(player, payload, config.sheetWebhookUrl(), config);
                if (!sent) {
                    enqueueFailedPayload(player.server, "player", payload, config);
                }
            } catch (Exception ex) {
                LOGGER.error("[Telemetry] Failed to export telemetry for {}", player.getGameProfile().getName(), ex);
            }
            return Optional.empty();
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerTelemetryConfig.TelemetryConfigValues config = PlayerTelemetryConfig.values();
        if (!TelemetryConfig.values().enabled() || !config.enabled()) {
            return;
        }

        if (config.sheetWebhookUrl() == null || config.sheetWebhookUrl().isBlank()) {
            LOGGER.warn("[Telemetry] Telemetry enabled but sheetWebhookUrl is blank. Skipping export.");
            return;
        }

        if (!config.exportOnLogout()) {
            return;
        }

        TelemetryConsentStore consentStore = TelemetryConsentStore.get(player.server);
        if (consentStore.getDecision(player.getUUID()) != ConsentDecision.ACCEPTED) {
            return;
        }

        long playTimeSeconds = getTotalPlayTimeSeconds(player);
        AsyncTaskManager.submitIoTask("player-telemetry-spark-report", () -> {
            try {
                String sparkReportUrl = config.includeSparkReport()
                        ? fetchSparkReportUrl(player).orElse(null)
                        : null;
                if (!TelemetrySampling.shouldSample("player_logout", config.sampleEveryNth(), config.sampleRatePercent())) {
                    return Optional.empty();
                }
                JsonObject payload = buildPayload(player, GeoInfo.empty(), AccountAgeInfo.empty(), playTimeSeconds,
                        sparkReportUrl, "logout", config);
                boolean sent = sendPayload(player, payload, config.sheetWebhookUrl(), config);
                if (!sent) {
                    enqueueFailedPayload(player.server, "player", payload, config);
                }
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
            HttpResponse<String> response = TelemetryHttp.sendWithRetry(
                    request,
                    config.retryMaxAttempts(),
                    Duration.ofMillis(config.retryBaseDelayMs()),
                    Duration.ofMillis(config.retryMaxDelayMs())
            );
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
            HttpResponse<String> response = TelemetryHttp.sendWithRetry(
                    request,
                    config.retryMaxAttempts(),
                    Duration.ofMillis(config.retryBaseDelayMs()),
                    Duration.ofMillis(config.retryMaxDelayMs())
            );
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

    private static JsonObject buildPayload(ServerPlayer player, GeoInfo geoInfo, AccountAgeInfo accountAge,
                                           long playTimeSeconds, String sparkReportUrl, String eventType,
                                           PlayerTelemetryConfig.TelemetryConfigValues config) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", TelemetryPayloads.SCHEMA_VERSION);
        String playerUuid = player.getUUID().toString();
        String playerName = player.getGameProfile().getName();
        if (config.hashPlayerIdentifiers()) {
            playerUuid = TelemetryHashing.hashIdentifier(playerUuid, config.identifierHashSalt());
            playerName = TelemetryHashing.hashIdentifier(playerName, config.identifierHashSalt());
        }
        payload.addProperty("uuid", playerUuid);
        payload.addProperty("player_name", playerName);
        payload.addProperty("identifiers_hashed", config.hashPlayerIdentifiers());
        payload.addProperty("event_type", eventType);
        Instant eventInstant = Instant.now();
        payload.addProperty("event_timestamp", eventInstant.toString());
        payload.addProperty("event_epoch_ms", eventInstant.toEpochMilli());
        payload.addProperty("total_play_time_seconds", playTimeSeconds);
        payload.add("state", geoInfo.state == null || geoInfo.state.isBlank()
                ? JsonNull.INSTANCE
                : jsonString(geoInfo.state));
        payload.add("country", geoInfo.country == null || geoInfo.country.isBlank()
                ? JsonNull.INSTANCE
                : jsonString(geoInfo.country));
        payload.add("account_age_days", accountAge.estimatedAgeDays == null
                ? JsonNull.INSTANCE
                : jsonNumber(accountAge.estimatedAgeDays));
        payload.add("account_age_reference", accountAge.referenceDate == null
                ? JsonNull.INSTANCE
                : jsonString(accountAge.referenceDate.toString()));
        payload.add("account_age_source", accountAge.source == null || accountAge.source.isBlank()
                ? JsonNull.INSTANCE
                : jsonString(accountAge.source));
        payload.add("spark_report_url", sparkReportUrl == null || sparkReportUrl.isBlank()
                ? JsonNull.INSTANCE
                : jsonString(sparkReportUrl));
        return payload;
    }

    private static boolean sendPayload(ServerPlayer player, JsonObject payload, String webhookUrl,
                                       PlayerTelemetryConfig.TelemetryConfigValues config) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }
        try {
            HttpResponse<String> response = TelemetryHttp.sendWithRetry(
                    TelemetryPayloads.buildRequest(webhookUrl, config.requestTimeoutSeconds(), payload),
                    config.retryMaxAttempts(),
                    Duration.ofMillis(config.retryBaseDelayMs()),
                    Duration.ofMillis(config.retryMaxDelayMs())
            );
            if (response.statusCode() / 100 != 2) {
                LOGGER.warn("[Telemetry] Sheet export failed (status {}).", response.statusCode());
                return false;
            } else {
                LOGGER.info("[Telemetry] Exported telemetry for {}.", player.getGameProfile().getName());
                return true;
            }
        } catch (Exception ex) {
            LOGGER.warn("[Telemetry] Sheet export failed: {}", ex.getMessage());
            return false;
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

    private static JsonElement jsonString(String value) {
        return value == null ? JsonNull.INSTANCE : GSON.toJsonTree(value);
    }

    private static JsonElement jsonNumber(Number value) {
        return value == null ? JsonNull.INSTANCE : GSON.toJsonTree(value);
    }

    private static long getTotalPlayTimeSeconds(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        int ticks = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        return ticks / 20L;
    }

    private static Optional<String> fetchSparkReportUrl(ServerPlayer player) {
        if (!ModList.get().isLoaded("spark")) {
            return Optional.empty();
        }

        try {
            Class<?> providerClass = Class.forName("me.lucko.spark.api.SparkProvider");
            Object sparkApi = providerClass.getMethod("get").invoke(null);
            if (sparkApi == null) {
                return Optional.empty();
            }

            Class<?> sparkApiClass = Class.forName("me.lucko.spark.common.api.SparkApi");
            if (!sparkApiClass.isInstance(sparkApi)) {
                return Optional.empty();
            }

            Field platformField = sparkApiClass.getDeclaredField("platform");
            platformField.setAccessible(true);
            Object platform = platformField.get(sparkApi);
            if (platform == null) {
                return Optional.empty();
            }

            Class<?> senderInterface = Class.forName("me.lucko.spark.common.command.sender.CommandSender");
            SparkCommandCapture capture = new SparkCommandCapture(player);
            Object senderProxy = Proxy.newProxyInstance(
                    senderInterface.getClassLoader(),
                    new Class<?>[]{senderInterface},
                    capture
            );

            Method executeCommand = platform.getClass().getMethod("executeCommand", senderInterface, String[].class);
            Object result = executeCommand.invoke(platform, senderProxy, (Object) new String[]{"report"});
            if (result instanceof CompletableFuture<?> future) {
                future.get(10, TimeUnit.SECONDS);
            }
            return capture.getReportUrl();
        } catch (Exception ex) {
            LOGGER.warn("[Telemetry] Spark report generation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static final class SparkCommandCapture implements InvocationHandler {
        private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
        private final ServerPlayer player;
        private Optional<String> reportUrl = Optional.empty();

        private SparkCommandCapture(ServerPlayer player) {
            this.player = player;
        }

        Optional<String> getReportUrl() {
            return reportUrl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("getName".equals(methodName)) {
                return player.getGameProfile().getName();
            }
            if ("getUniqueId".equals(methodName)) {
                return player.getUUID();
            }
            if ("isPlayer".equals(methodName)) {
                return true;
            }
            if ("hasPermission".equals(methodName)) {
                return true;
            }
            if ("sendMessage".equals(methodName) && args != null && args.length > 0) {
                captureReportUrl(args[0]);
                return null;
            }
            if ("toData".equals(methodName)) {
                return createSenderData();
            }
            if ("hashCode".equals(methodName)) {
                return Objects.hash(player.getUUID());
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("toString".equals(methodName)) {
                return "SparkCommandCapture{" + player.getGameProfile().getName() + "}";
            }
            return null;
        }

        private void captureReportUrl(Object component) {
            String message = String.valueOf(component);
            Matcher matcher = URL_PATTERN.matcher(message);
            if (matcher.find()) {
                reportUrl = Optional.of(matcher.group());
                return;
            }
            String json = trySerializeComponent(component);
            if (json == null) {
                return;
            }
            matcher = URL_PATTERN.matcher(json);
            if (matcher.find()) {
                reportUrl = Optional.of(matcher.group());
            }
        }

        private String trySerializeComponent(Object component) {
            try {
                Class<?> serializerClass = Class.forName("me.lucko.spark.lib.adventure.text.serializer.gson.GsonComponentSerializer");
                Object serializer = serializerClass.getMethod("gson").invoke(null);
                Object jsonElement = serializerClass.getMethod("serializeToTree", Class.forName("me.lucko.spark.lib.adventure.text.Component"))
                        .invoke(serializer, component);
                return String.valueOf(jsonElement);
            } catch (Exception ex) {
                return null;
            }
        }

        private Object createSenderData() {
            try {
                Class<?> dataClass = Class.forName("me.lucko.spark.common.command.sender.CommandSender$Data");
                return dataClass.getConstructor(String.class, UUID.class)
                        .newInstance(player.getGameProfile().getName(), player.getUUID());
            } catch (Exception ex) {
                return null;
            }
        }
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

    private record CachedGeoInfo(GeoInfo info, Instant timestamp) {
    }

    private record CachedAccountAge(AccountAgeInfo info, Instant timestamp) {
    }

    private static GeoInfo resolveGeoInfo(UUID uuid, String ipAddress, PlayerTelemetryConfig.TelemetryConfigValues config) {
        if (uuid == null) {
            return fetchGeoInfo(ipAddress, config);
        }
        int ttlSeconds = config.geoCacheTtlSeconds();
        CachedGeoInfo cached = GEO_CACHE.get(uuid);
        if (cached != null) {
            if (ttlSeconds > 0 && Duration.between(cached.timestamp(), Instant.now()).toSeconds() <= ttlSeconds) {
                return cached.info();
            }
            if (ttlSeconds <= 0) {
                GEO_CACHE.remove(uuid);
            }
        }
        GeoInfo info = fetchGeoInfo(ipAddress, config);
        if (ttlSeconds > 0) {
            GEO_CACHE.put(uuid, new CachedGeoInfo(info, Instant.now()));
        }
        return info;
    }

    private static AccountAgeInfo resolveAccountAge(UUID uuid, PlayerTelemetryConfig.TelemetryConfigValues config) {
        if (uuid == null) {
            return AccountAgeInfo.empty();
        }
        int ttlSeconds = config.accountAgeCacheTtlSeconds();
        CachedAccountAge cached = ACCOUNT_AGE_CACHE.get(uuid);
        if (cached != null) {
            if (ttlSeconds > 0 && Duration.between(cached.timestamp(), Instant.now()).toSeconds() <= ttlSeconds) {
                return cached.info();
            }
            if (ttlSeconds <= 0) {
                ACCOUNT_AGE_CACHE.remove(uuid);
            }
        }
        AccountAgeInfo info = fetchAccountAge(uuid, config);
        if (ttlSeconds > 0) {
            ACCOUNT_AGE_CACHE.put(uuid, new CachedAccountAge(info, Instant.now()));
        }
        return info;
    }

    private static void enqueueFailedPayload(net.minecraft.server.MinecraftServer server, String type,
                                             JsonObject payload, PlayerTelemetryConfig.TelemetryConfigValues config) {
        TelemetryConfig.TelemetryValues telemetryConfig = TelemetryConfig.values();
        TelemetryQueue.PendingTelemetryPayload pending = new TelemetryQueue.PendingTelemetryPayload(
                type,
                payload,
                config.sheetWebhookUrl(),
                config.requestTimeoutSeconds(),
                config.retryMaxAttempts(),
                Duration.ofMillis(config.retryBaseDelayMs()),
                Duration.ofMillis(config.retryMaxDelayMs())
        );
        TelemetryQueue.get(server).enqueue(pending, telemetryConfig.queueMaxSize());
    }

}
