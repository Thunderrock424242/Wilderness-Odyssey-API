package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Persistent per-server queue for telemetry payloads that retries delivery and
 * stores pending events on disk safely without blocking the main thread.
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

    public static TelemetryQueue get(MinecraftServer server) {
        return QUEUES.computeIfAbsent(server, TelemetryQueue::createForServer);
    }

    private static TelemetryQueue createForServer(MinecraftServer server) {
        Path configDir = server.getFile("config/wildernessodysseyapi");
        Path spoolFile = configDir.resolve("telemetry-queue.jsonl");
        return new TelemetryQueue(spoolFile);
    }

    /**
     * Adds a payload to the queue. Synchronized locally to protect the Deque,
     * but disk I/O is pushed to a background thread to prevent micro-stutters.
     */
    public void enqueue(PendingTelemetryPayload payload, int maxQueueSize) {
        synchronized (this) {
            if (queue.size() >= maxQueueSize) {
                queue.pollFirst();
                failedCount.incrementAndGet();
            }
            queue.addLast(payload);
        }

        // THE FIX: Do not write to disk on the main server thread
        AsyncTaskManager.submitIoTask("Telemetry_Persist", () -> {
            synchronized (this) {
                persistAll();
            }
            return Optional.empty();
        });
    }

    /**
     * Attempts to send queued payloads up to {@code maxBatchSize}.
     * Network I/O is performed OUTSIDE of the synchronized lock to prevent server freezes.
     */
    public int flush(int maxBatchSize) {
        List<PendingTelemetryPayload> batch = new ArrayList<>();

        // 1. Grab the items quickly and release the lock
        synchronized (this) {
            while (!queue.isEmpty() && batch.size() < maxBatchSize) {
                batch.add(queue.pollFirst());
            }
        }

        if (batch.isEmpty()) {
            return 0;
        }

        int attempted = 0;
        boolean changed = false;
        List<PendingTelemetryPayload> failed = new ArrayList<>();

        // 2. Perform slow network I/O safely (lock is released, main thread is free!)
        for (PendingTelemetryPayload payload : batch) {
            attempted++;
            boolean sent = payload.send();
            if (sent) {
                lastSuccess = Instant.now();
                changed = true;
            } else {
                payload.incrementAttempts();
                failed.add(payload);
                changed = true;
            }
        }

        // 3. Re-acquire lock to insert failures and save state
        synchronized (this) {
            for (PendingTelemetryPayload payload : failed) {
                queue.addLast(payload);
                failedCount.incrementAndGet();
            }
            if (changed) {
                persistAll();
            }
        }
        return attempted;
    }

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

    // The PendingTelemetryPayload and TelemetryQueueStats classes remain exactly the same as your original file
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

        public void incrementAttempts() {
            attempts++;
            lastAttempt = Instant.now();
        }
    }

    public record TelemetryQueueStats(int pending, int failed, Instant lastSuccess) {
        public Optional<Instant> lastSuccessOptional() {
            return Optional.ofNullable(lastSuccess);
        }
    }
}