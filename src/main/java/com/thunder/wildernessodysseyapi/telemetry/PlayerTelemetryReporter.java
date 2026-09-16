package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.ticktoklib.api.TickTokAPI;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
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

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Collects player country and account age data and sends it to a Google Sheets webhook.
 */
public final class PlayerTelemetryReporter {
    private static final PlayerTelemetrySessions SESSIONS = new PlayerTelemetrySessions();
    private static final int MAX_SPARK_REPORT_WAIT_SECONDS = 5;
    private static final Object SPARK_REPORT_LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, CachedGeoInfo> GEO_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedAccountAge> ACCOUNT_AGE_CACHE = new ConcurrentHashMap<>();

    private PlayerTelemetryReporter() {
    }

    /** Captures a sampled session without gating on physical client/server distribution. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
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

        if (!TelemetrySampling.shouldSample("player_session", config.sampleEveryNth(), config.sampleRatePercent())) {
            return;
        }

        var session = SESSIONS.begin(player.getUUID(), Instant.now(), System.nanoTime());
        if (session == null) {
            return;
        }
        PlayerSnapshot snapshot = captureSnapshot(player, session, config);
        TelemetryQueue queue = TelemetryQueue.get(player.server);
        // Persist a usable report before optional lookups or HTTP can fail or be interrupted.
        var pending = enqueuePayload(queue, "player", buildPayload(snapshot, GeoInfo.empty(),
                AccountAgeInfo.empty(), null, "login", config), config.sheetWebhookUrl(), config);
        AsyncTaskManager.trySubmitIoWork("player-telemetry-enrichment", () -> {
            GeoInfo geo = resolveGeoInfo(snapshot.uuid(), snapshot.ipAddress(), config);
            AccountAgeInfo age = resolveAccountAge(snapshot.uuid(), config);
            queue.enrich(pending, buildPayload(snapshot, geo, age, null, "login", config));
            TelemetryQueueProcessor.requestFlush(queue, TelemetryConfig.values().queueFlushBatchSize());
        });
    }

    /** Captures the matching session end on the logical server, including dedicated servers. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var session = SESSIONS.end(player.getUUID());
        PlayerTelemetryConfig.TelemetryConfigValues config = PlayerTelemetryConfig.values();
        if (session == null || !TelemetryConfig.values().enabled() || !config.enabled()
                || !config.exportOnLogout() || config.sheetWebhookUrl() == null || config.sheetWebhookUrl().isBlank()) {
            return;
        }
        PlayerSnapshot snapshot = captureSnapshot(player, session, config);
        TelemetryQueue queue = TelemetryQueue.get(player.server);
        var geo = GEO_CACHE.get(snapshot.uuid());
        var age = ACCOUNT_AGE_CACHE.get(snapshot.uuid());
        var pending = enqueuePayload(queue, "player", buildPayload(snapshot,
                geo == null ? GeoInfo.empty() : geo.info(), age == null ? AccountAgeInfo.empty() : age.info(),
                null, "logout", config), config.sheetWebhookUrl(), config);
        if (config.includeSparkReport()) {
            var server = player.server;
            AsyncTaskManager.trySubmitIoWork("player-telemetry-spark-report", () -> {
                String report = fetchSparkReportUrl(snapshot,
                        cappedSparkTimeout(config.logoutBlockTimeoutSeconds()), server).orElse(null);
                if (report != null && config.sparkWebhookUrl() != null && !config.sparkWebhookUrl().isBlank()) {
                    enqueuePayload(queue, "spark", buildSparkPayload(snapshot, report, config),
                            config.sparkWebhookUrl(), config);
                } else if (report != null) {
                    queue.enrich(pending, buildPayload(snapshot, GeoInfo.empty(), AccountAgeInfo.empty(),
                            report, "logout", config));
                }
                TelemetryQueueProcessor.requestFlush(queue, TelemetryConfig.values().queueFlushBatchSize());
            });
        }
        TelemetryQueueProcessor.requestFlush(queue, TelemetryConfig.values().queueFlushBatchSize());
    }
    /** Captures final session ends before the shared worker pool and retry spool are closed. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer player : java.util.List.copyOf(event.getServer().getPlayerList().getPlayers())) {
            // Removing the session now also deduplicates the subsequent vanilla logout event.
            onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
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
            LOGGER.warn("[Telemetry] Geo IP lookup failed (request failed).");
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
            LOGGER.warn("[Telemetry] Account age lookup failed (request failed).");
            return AccountAgeInfo.empty();
        }
    }

    static JsonObject buildPayload(PlayerSnapshot snapshot, GeoInfo geoInfo, AccountAgeInfo accountAge,
                                           String sparkReportUrl, String eventType,
                                           PlayerTelemetryConfig.TelemetryConfigValues config) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", TelemetryPayloads.SCHEMA_VERSION);
        String playerUuid = snapshot.uuid().toString();
        String playerName = snapshot.playerName();
        if (config.hashPlayerIdentifiers()) {
            playerUuid = TelemetryHashing.hashIdentifier(playerUuid, config.identifierHashSalt());
            playerName = TelemetryHashing.hashIdentifier(playerName, config.identifierHashSalt());
        }
        payload.addProperty("uuid", playerUuid);
        payload.addProperty("player_name", playerName);
        payload.addProperty("identifiers_hashed", config.hashPlayerIdentifiers());
        payload.addProperty("event_type", eventType);
        payload.addProperty("event_timestamp", snapshot.eventTime().toString());
        payload.addProperty("event_epoch_ms", snapshot.eventTime().toEpochMilli());
        payload.addProperty("total_play_time_seconds", snapshot.playTimeSeconds());
        if (snapshot.sessionId() != null) {
            payload.addProperty("session_id", snapshot.sessionId().toString());
            payload.addProperty("session_started_at", snapshot.sessionStartedAt().toString());
            payload.addProperty("session_duration_seconds", snapshot.sessionDurationSeconds());
        }
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
        return Math.round(TickTokAPI.toSeconds(ticks));
    }

    // Minecraft-owned state is captured on the login/logout event thread. Only
    // this immutable data object crosses into the telemetry worker pool.
    private static PlayerSnapshot captureSnapshot(ServerPlayer player, PlayerTelemetrySessions.Session session,
                                                  PlayerTelemetryConfig.TelemetryConfigValues config) {
        return new PlayerSnapshot(
                player.getUUID(),
                player.getGameProfile().getName(),
                config.geoIpEndpoint() == null || config.geoIpEndpoint().isBlank() ? null : resolveIpAddress(player),
                getTotalPlayTimeSeconds(player),
                Instant.now(), session.id(), session.startedAt(), session.durationSeconds(System.nanoTime())
        );
    }

    private static Optional<String> fetchSparkReportUrl(PlayerSnapshot snapshot, int timeoutSeconds, net.minecraft.server.MinecraftServer server) {
        // Spark's reflective command surface is not documented as concurrently
        // callable. Serialize report requests on telemetry workers while keeping
        // all live Minecraft objects out of the adapter.
        synchronized (SPARK_REPORT_LOCK) {
            return fetchSparkReportUrlLocked(snapshot, timeoutSeconds, server);
        }
    }

    private static Optional<String> fetchSparkReportUrlLocked(PlayerSnapshot snapshot, int timeoutSeconds, net.minecraft.server.MinecraftServer server) {
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
            SparkCommandCapture capture = new SparkCommandCapture(snapshot.playerName(), snapshot.uuid());
            Object senderProxy = Proxy.newProxyInstance(
                    senderInterface.getClassLoader(),
                    new Class<?>[]{senderInterface},
                    capture
            );

            Method executeCommand = platform.getClass().getMethod("executeCommand", senderInterface, String[].class);
            CompletableFuture<Object> command = new CompletableFuture<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            server.execute(() -> {
                if (command.isDone() || server.isStopped()) {
                    return;
                }
                try {
                    command.complete(executeCommand.invoke(platform, senderProxy, (Object) new String[]{"report"}));
                } catch (Exception failure) {
                    command.completeExceptionally(failure);
                }
            });
            Object result;
            try {
                result = command.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            } finally {
                command.cancel(false);
            }
            if (result instanceof CompletableFuture<?> future) {
                future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
            return capture.getReportUrl();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("[Telemetry] Spark report generation failed (request failed).");
            return Optional.empty();
        }
    }

    static int cappedSparkTimeout(int configuredTimeoutSeconds) {
        return Math.max(1, Math.min(MAX_SPARK_REPORT_WAIT_SECONDS, configuredTimeoutSeconds));
    }

    private static JsonObject buildSparkPayload(PlayerSnapshot snapshot, String sparkReportUrl, PlayerTelemetryConfig.TelemetryConfigValues config) {
        Instant now = Instant.now();
        JsonObject payload = new JsonObject();
        payload.addProperty("event_type", "spark_report");
        payload.addProperty("player_name", config.hashPlayerIdentifiers() ? TelemetryHashing.hashIdentifier(snapshot.playerName(), config.identifierHashSalt()) : snapshot.playerName());
        payload.addProperty("player_uuid", config.hashPlayerIdentifiers() ? TelemetryHashing.hashIdentifier(snapshot.uuid().toString(), config.identifierHashSalt()) : snapshot.uuid().toString());
        payload.addProperty("spark_report_url", sparkReportUrl);
        payload.addProperty("timestamp", now.toString());
        payload.addProperty("report_date_utc", now.toString().substring(0, 10));
        payload.addProperty("report_time_utc", now.toString().substring(11, 19));
        payload.addProperty("play_time_seconds", snapshot.playTimeSeconds());
        payload.addProperty("play_time_minutes", Math.round((snapshot.playTimeSeconds() / 60.0) * 100.0) / 100.0);
        return payload;
    }

    private static final class SparkCommandCapture implements InvocationHandler {
        private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
        private final String playerName;
        private final UUID playerUuid;
        private volatile Optional<String> reportUrl = Optional.empty();

        private SparkCommandCapture(String playerName, UUID playerUuid) {
            this.playerName = playerName;
            this.playerUuid = playerUuid;
        }

        Optional<String> getReportUrl() {
            return reportUrl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("getName".equals(methodName)) {
                return playerName;
            }
            if ("getUniqueId".equals(methodName)) {
                return playerUuid;
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
                return Objects.hash(playerUuid);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("toString".equals(methodName)) {
                return "SparkCommandCapture{" + playerName + "}";
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
                        .newInstance(playerName, playerUuid);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    record PlayerSnapshot(UUID uuid, String playerName, String ipAddress, long playTimeSeconds, Instant eventTime,
                          UUID sessionId, Instant sessionStartedAt, long sessionDurationSeconds) {
        PlayerSnapshot(UUID uuid, String playerName, String ipAddress, long playTimeSeconds, Instant eventTime) {
            this(uuid, playerName, ipAddress, playTimeSeconds, eventTime, null, eventTime, 0);
        }
        PlayerSnapshot {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(eventTime, "eventTime");
        }
    }

    record GeoInfo(String state, String country) {
        static GeoInfo empty() {
            return new GeoInfo(null, null);
        }
    }

    record AccountAgeInfo(Long estimatedAgeDays, Instant referenceDate, String source) {
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

    /** Clears player-derived cache entries after telemetry is disabled or the server stops. */
    public static void clearCaches() {
        SESSIONS.clear();
        if (!GEO_CACHE.isEmpty()) {
            GEO_CACHE.clear();
        }
        if (!ACCOUNT_AGE_CACHE.isEmpty()) {
            ACCOUNT_AGE_CACHE.clear();
        }
    }

    /** Removes expired cache entries independently of a repeat lookup by the same UUID. */
    static void evictExpiredCaches(PlayerTelemetryConfig.TelemetryConfigValues config, Instant now) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(now, "now");
        removeExpiredGeoEntries(config.geoCacheTtlSeconds(), now);
        removeExpiredAccountEntries(config.accountAgeCacheTtlSeconds(), now);
    }

    private static void removeExpiredGeoEntries(int ttlSeconds, Instant now) {
        if (ttlSeconds <= 0) {
            GEO_CACHE.clear();
            return;
        }
        Instant oldestAllowed = now.minusSeconds(ttlSeconds);
        GEO_CACHE.entrySet().removeIf(entry -> entry.getValue().timestamp().isBefore(oldestAllowed));
    }

    private static void removeExpiredAccountEntries(int ttlSeconds, Instant now) {
        if (ttlSeconds <= 0) {
            ACCOUNT_AGE_CACHE.clear();
            return;
        }
        Instant oldestAllowed = now.minusSeconds(ttlSeconds);
        ACCOUNT_AGE_CACHE.entrySet().removeIf(entry -> entry.getValue().timestamp().isBefore(oldestAllowed));
    }

    static GeoInfo resolveGeoInfo(UUID uuid, String ipAddress, PlayerTelemetryConfig.TelemetryConfigValues config) {
        if (uuid == null) {
            return fetchGeoInfo(ipAddress, config);
        }
        int ttlSeconds = config.geoCacheTtlSeconds();
        CachedGeoInfo cached = GEO_CACHE.get(uuid);
        if (cached != null) {
            if (ttlSeconds > 0) {
                Instant now = Instant.now();
                if (Duration.between(cached.timestamp(), now).toSeconds() <= ttlSeconds) {
                    return cached.info();
                }
            }
            if (ttlSeconds <= 0) {
                GEO_CACHE.remove(uuid);
            }
        }
        GeoInfo info = fetchGeoInfo(ipAddress, config);
        if (ttlSeconds > 0 && GEO_CACHE.size() < 4096) {
            GEO_CACHE.put(uuid, new CachedGeoInfo(info, Instant.now()));
        }
        return info;
    }

    static AccountAgeInfo resolveAccountAge(UUID uuid, PlayerTelemetryConfig.TelemetryConfigValues config) {
        if (uuid == null) {
            return AccountAgeInfo.empty();
        }
        int ttlSeconds = config.accountAgeCacheTtlSeconds();
        CachedAccountAge cached = ACCOUNT_AGE_CACHE.get(uuid);
        if (cached != null) {
            if (ttlSeconds > 0) {
                Instant now = Instant.now();
                if (Duration.between(cached.timestamp(), now).toSeconds() <= ttlSeconds) {
                    return cached.info();
                }
            }
            if (ttlSeconds <= 0) {
                ACCOUNT_AGE_CACHE.remove(uuid);
            }
        }
        AccountAgeInfo info = fetchAccountAge(uuid, config);
        if (ttlSeconds > 0 && ACCOUNT_AGE_CACHE.size() < 4096) {
            ACCOUNT_AGE_CACHE.put(uuid, new CachedAccountAge(info, Instant.now()));
        }
        return info;
    }

    private static TelemetryQueue.PendingTelemetryPayload enqueuePayload(TelemetryQueue queue, String type, JsonObject payload,
                                             String webhookUrl,
                                             PlayerTelemetryConfig.TelemetryConfigValues config) {
        TelemetryConfig.TelemetryValues telemetryConfig = TelemetryConfig.values();
        TelemetryQueue.PendingTelemetryPayload pending = new TelemetryQueue.PendingTelemetryPayload(
                type,
                payload,
                webhookUrl,
                config.requestTimeoutSeconds(),
                config.retryMaxAttempts(),
                Duration.ofMillis(config.retryBaseDelayMs()),
                Duration.ofMillis(config.retryMaxDelayMs())
        );
        queue.enqueue(pending, telemetryConfig.queueMaxSize());
        return pending;
    }

}
