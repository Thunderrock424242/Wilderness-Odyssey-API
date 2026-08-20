package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Persistent per-server queue for telemetry payloads that retries delivery and
 * stores pending events on disk safely without blocking the main thread.
 */
public final class TelemetryQueue {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantJsonAdapter())
            .create();
    private static final Map<MinecraftServer, TelemetryQueue> QUEUES = new ConcurrentHashMap<>();

    private final Deque<PendingTelemetryPayload> queue = new ArrayDeque<>();
    private final Path spoolPath;
    private final PersistenceScheduler persistenceScheduler;
    private final Object persistenceLock = new Object();
    private final AtomicBoolean persistenceDirty = new AtomicBoolean(false);
    private final AtomicBoolean persistenceScheduled = new AtomicBoolean(false);
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger failedCount = new AtomicInteger();
    private Instant lastSuccess;

    private TelemetryQueue(Path spoolPath) {
        this(spoolPath, task -> AsyncTaskManager.trySubmitIoWork("Telemetry_Persist", task));
    }

    TelemetryQueue(Path spoolPath, PersistenceScheduler persistenceScheduler) {
        this.spoolPath = spoolPath;
        this.persistenceScheduler = persistenceScheduler;
        loadFromDisk();
    }

    public static TelemetryQueue get(MinecraftServer server) {
        return QUEUES.computeIfAbsent(server, TelemetryQueue::createForServer);
    }

    /**
     * Persists and releases the retry queue owned by a stopped server.
     *
     * <p>This must run after telemetry producers and the shared async executor
     * have stopped accepting work. The synchronous final snapshot keeps server
     * shutdown from losing payloads when no worker remains available.</p>
     */
    public static void shutdown(MinecraftServer server) {
        if (server == null) {
            return;
        }
        TelemetryQueue queue = QUEUES.remove(server);
        if (queue != null) {
            queue.close();
        }
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
        if (payload == null || closed.get()) {
            return;
        }
        synchronized (this) {
            if (closed.get()) {
                return;
            }
            int boundedQueueSize = Math.max(1, maxQueueSize);
            while (queue.size() >= boundedQueueSize) {
                queue.pollFirst();
                failedCount.incrementAndGet();
            }
            queue.addLast(payload);
        }
        schedulePersistence();
    }

    /**
     * Attempts to send queued payloads up to {@code maxBatchSize}.
     * Network I/O is performed OUTSIDE of the synchronized lock to prevent server freezes.
     */
    public int flush(int maxBatchSize) {
        List<PendingTelemetryPayload> batch;

        // Snapshot rather than removing in-flight entries. A concurrent server
        // shutdown can then persist an at-least-once copy instead of losing work.
        synchronized (this) {
            batch = queue.stream().limit(Math.max(1, maxBatchSize)).toList();
        }

        if (batch.isEmpty()) {
            return 0;
        }

        int attempted = 0;
        List<PendingTelemetryPayload> sentPayloads = new ArrayList<>();
        List<PendingTelemetryPayload> failed = new ArrayList<>();

        // 2. Perform slow network I/O safely (lock is released, main thread is free!)
        for (PendingTelemetryPayload payload : batch) {
            attempted++;
            boolean sent = payload.send();
            if (sent) {
                this.lastSuccess = Instant.now();
                sentPayloads.add(payload);
            } else {
                payload.incrementAttempts();
                failed.add(payload);
            }
        }

        // Apply only the delivery results. Failed entries remain queue-owned
        // during I/O, then rotate to the tail so one bad endpoint cannot starve
        // later payloads.
        synchronized (this) {
            for (PendingTelemetryPayload payload : sentPayloads) {
                queue.removeFirstOccurrence(payload);
            }
            for (PendingTelemetryPayload payload : failed) {
                if (queue.removeFirstOccurrence(payload)) {
                    queue.addLast(payload);
                }
                failedCount.incrementAndGet();
            }
        }
        schedulePersistence();
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
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("[Telemetry] Failed to load telemetry queue: {}", ex.getMessage());
        }
    }

    private void schedulePersistence() {
        persistenceDirty.set(true);
        if (closed.get() || !persistenceScheduled.compareAndSet(false, true)) {
            return;
        }
        if (!persistenceScheduler.schedule(this::persistScheduledSnapshot)) {
            // Keep the dirty bit set. A later mutation or the synchronous
            // lifecycle close will retry without running disk I/O inline.
            persistenceScheduled.set(false);
        }
    }

    private void persistScheduledSnapshot() {
        boolean persisted = false;
        try {
            List<PendingTelemetryPayload> snapshot;
            synchronized (this) {
                snapshot = List.copyOf(queue);
                persistenceDirty.set(false);
            }
            persisted = persistSnapshot(snapshot);
            if (!persisted) {
                persistenceDirty.set(true);
            }
        } finally {
            persistenceScheduled.set(false);
            // A mutation that raced the snapshot receives exactly one follow-up
            // write. Disk failures wait for a later mutation instead of spinning.
            if (persisted && persistenceDirty.get() && !closed.get()) {
                schedulePersistence();
            }
        }
    }

    private boolean persistSnapshot(List<PendingTelemetryPayload> snapshot) {
        synchronized (persistenceLock) {
            return writeSnapshot(snapshot);
        }
    }

    private boolean writeSnapshot(List<PendingTelemetryPayload> snapshot) {
        try {
            Files.createDirectories(spoolPath.getParent());
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Failed to create telemetry queue directory: {}", ex.getMessage());
            return false;
        }
        Path temporaryPath = spoolPath.resolveSibling(spoolPath.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporaryPath)) {
            for (PendingTelemetryPayload payload : snapshot) {
                writer.write(GSON.toJson(payload));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("[Telemetry] Failed to write telemetry queue snapshot: {}", ex.getMessage());
            return false;
        }
        try {
            try {
                Files.move(temporaryPath, spoolPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, spoolPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Failed to publish telemetry queue snapshot: {}", ex.getMessage());
            return false;
        }
    }

    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<PendingTelemetryPayload> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(queue);
        }
        if (persistSnapshot(snapshot)) {
            persistenceDirty.set(false);
        }
    }

    boolean tryBeginFlush() {
        return !closed.get() && flushInProgress.compareAndSet(false, true);
    }

    void finishFlush() {
        flushInProgress.set(false);
    }

    @FunctionalInterface
    interface PersistenceScheduler {
        boolean schedule(Runnable task);
    }

    private static final class InstantJsonAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public com.google.gson.JsonElement serialize(
                Instant source,
                java.lang.reflect.Type type,
                JsonSerializationContext context
        ) {
            return source == null ? com.google.gson.JsonNull.INSTANCE : context.serialize(source.toString());
        }

        @Override
        public Instant deserialize(
                com.google.gson.JsonElement json,
                java.lang.reflect.Type type,
                JsonDeserializationContext context
        ) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            try {
                return Instant.parse(json.getAsString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid telemetry timestamp", exception);
            }
        }
    }

    /** A retryable telemetry request stored in the server-owned spool. */
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
