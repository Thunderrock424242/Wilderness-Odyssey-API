package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Persistent per-server queue for telemetry payloads that retries delivery and
 * stores pending events on disk.
 */
public final class TelemetryQueue {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<MinecraftServer, TelemetryQueue> QUEUES = new ConcurrentHashMap<>();

    private final Deque<PendingTelemetryPayload> queue = new ArrayDeque<>();
    private final Path spoolPath;
    private final AtomicInteger failedCount = new AtomicInteger();
    private Instant lastSuccess;

    private TelemetryQueue(Path spoolPath) {
        this.spoolPath = spoolPath;
        loadFromDisk();
    }

    /**
     * Returns the shared telemetry queue for a running server instance.
     */
    public static TelemetryQueue get(MinecraftServer server) {
        return QUEUES.computeIfAbsent(server, TelemetryQueue::createForServer);
    }

    private static TelemetryQueue createForServer(MinecraftServer server) {
        Path configDir = server.getFile("config/wildernessodysseyapi");
        Path spoolFile = configDir.resolve("telemetry-queue.jsonl");
        return new TelemetryQueue(spoolFile);
    }

    /**
     * Adds a payload to the queue, dropping the oldest entry when the queue is
     * already full.
     */
    public synchronized void enqueue(PendingTelemetryPayload payload, int maxQueueSize) {
        if (queue.size() >= maxQueueSize) {
            queue.pollFirst();
            failedCount.incrementAndGet();
        }
        queue.addLast(payload);
        persistAll();
    }

    /**
     * Attempts to send queued payloads up to {@code maxBatchSize}.
     *
     * @return Number of payloads attempted in this flush cycle.
     */
    public synchronized int flush(int maxBatchSize) {
        int attempted = 0;
        boolean changed = false;
        while (!queue.isEmpty() && attempted < maxBatchSize) {
            PendingTelemetryPayload payload = queue.pollFirst();
            attempted++;
            boolean sent = payload.send();
            if (sent) {
                lastSuccess = Instant.now();
                changed = true;
            } else {
                payload.incrementAttempts();
                queue.addLast(payload);
                failedCount.incrementAndGet();
                changed = true;
            }
        }
        if (changed) {
            persistAll();
        }
        return attempted;
    }

    /**
     * @return Snapshot of queue depth, failures, and last successful send time.
     */
    public synchronized TelemetryQueueStats stats() {
        return new TelemetryQueueStats(queue.size(), failedCount.get(), lastSuccess);
    }

    private void loadFromDisk() {
        if (!Files.exists(spoolPath)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(spoolPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                PendingTelemetryPayload payload = GSON.fromJson(line, PendingTelemetryPayload.class);
                if (payload != null && payload.payload != null && payload.webhookUrl != null) {
                    queue.addLast(payload);
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Failed to load telemetry queue: {}", ex.getMessage());
        }
    }

    private void persistAll() {
        try {
            Files.createDirectories(spoolPath.getParent());
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Failed to create telemetry queue directory: {}", ex.getMessage());
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(spoolPath)) {
            for (PendingTelemetryPayload payload : queue) {
                writer.write(GSON.toJson(payload));
                writer.newLine();
            }
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Failed to persist telemetry queue: {}", ex.getMessage());
        }
    }

    /**
     * One queued telemetry event plus retry metadata.
     */
    public static final class PendingTelemetryPayload {
        private String type;
        private JsonObject payload;
        private String webhookUrl;
        private int timeoutSeconds;
        private int maxRetries;
        private long retryBaseDelayMs;
        private long retryMaxDelayMs;
        private int attempts;
        private Instant createdAt;
        private Instant lastAttempt;

        @SuppressWarnings("unused")
        private PendingTelemetryPayload() {
        }

        /**
         * Creates a queued payload entry with retry and timeout settings.
         */
        public PendingTelemetryPayload(String type, JsonObject payload, String webhookUrl, int timeoutSeconds,
                                       int maxRetries, Duration baseDelay, Duration maxDelay) {
            this.type = type;
            this.payload = payload;
            this.webhookUrl = webhookUrl;
            this.timeoutSeconds = timeoutSeconds;
            this.maxRetries = maxRetries;
            this.retryBaseDelayMs = baseDelay.toMillis();
            this.retryMaxDelayMs = maxDelay.toMillis();
            this.attempts = 0;
            this.createdAt = Instant.now();
        }

        /**
         * Sends this payload through the telemetry HTTP client with retries.
         */
        public boolean send() {
            if (payload == null || webhookUrl == null || webhookUrl.isBlank()) {
                return false;
            }
            try {
                var response = TelemetryHttp.sendWithRetry(
                        TelemetryPayloads.buildRequest(webhookUrl, timeoutSeconds, payload),
                        maxRetries,
                        Duration.ofMillis(retryBaseDelayMs),
                        Duration.ofMillis(retryMaxDelayMs)
                );
                return response != null && response.statusCode() / 100 == 2;
            } catch (Exception ex) {
                LOGGER.warn("[Telemetry] Queued payload send failed ({}): {}", type, ex.getMessage());
                return false;
            }
        }

        /**
         * Increments attempt metadata after a failed send attempt.
         */
        public void incrementAttempts() {
            attempts++;
            lastAttempt = Instant.now();
        }
    }

    /**
     * Immutable queue statistics view.
     */
    public record TelemetryQueueStats(int pending, int failed, Instant lastSuccess) {
        public Optional<Instant> lastSuccessOptional() {
            return Optional.ofNullable(lastSuccess);
        }
    }
}
