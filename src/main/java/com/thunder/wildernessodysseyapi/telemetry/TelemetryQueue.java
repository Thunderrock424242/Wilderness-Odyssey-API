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
    private static final int MAX_ROW_CHARS = 256 * 1024;
    private static final int MAX_SPOOL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private long queuedBytes;
    private final AtomicInteger droppedCount = new AtomicInteger();
    private volatile int inFlight;

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
    private volatile Instant lastSuccess;

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
            if (!payload.normalizeAfterLoad() || payload.reservedBytes() > MAX_ROW_CHARS) {
                droppedCount.incrementAndGet();
                LOGGER.warn("[Telemetry] Rejected an invalid or oversized report.");
                return;
            }
            int boundedQueueSize = Math.clamp(maxQueueSize, 1, MAX_ENTRIES);
            while (!queue.isEmpty() && (queue.size() >= boundedQueueSize
                    || queuedBytes + payload.reservedBytes() > MAX_SPOOL_BYTES)) {
                queuedBytes -= queue.removeFirst().reservedBytes();
                droppedCount.incrementAndGet();
                LOGGER.warn("[Telemetry] Queue capacity reached; discarded its oldest report.");
            }
            queue.addLast(payload);
            queuedBytes += payload.reservedBytes();
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
            batch = queue.stream().limit(Math.clamp(maxBatchSize, 1, 512)).toList();
            inFlight = batch.size();
        }

        if (batch.isEmpty()) {
            return 0;
        }

        int attempted = 0;
        List<PendingTelemetryPayload> sentPayloads = new ArrayList<>();
        List<PendingTelemetryPayload> failed = new ArrayList<>();

        // 2. Perform slow network I/O safely (lock is released, main thread is free!)
        for (PendingTelemetryPayload payload : batch) {
            if (closed.get() || Thread.currentThread().isInterrupted()) {
                break;
            }
            attempted++;
            boolean sent = payload.send();
            if (sent) {
                this.lastSuccess = Instant.now();
                sentPayloads.add(payload);
            } else {
                failed.add(payload);
            }
        }

        // Apply only the delivery results. Failed entries remain queue-owned
        // during I/O, then rotate to the tail so one bad endpoint cannot starve
        // later payloads.
        synchronized (this) {
            for (PendingTelemetryPayload payload : sentPayloads) {
                if (queue.removeFirstOccurrence(payload)) {
                    queuedBytes -= payload.reservedBytes();
                }
            }
            for (PendingTelemetryPayload payload : failed) {
                payload.incrementAttempts();
                if (queue.removeFirstOccurrence(payload)) {
                    queue.addLast(payload);
                }
                failedCount.incrementAndGet();
            }
        }
        inFlight = 0;
        schedulePersistence();
        return attempted;
    }

    public synchronized TelemetryQueueStats stats() {
        return new TelemetryQueueStats(queue.size(), failedCount.get(), lastSuccess,
                (int) queue.stream().filter(payload -> payload.attempts > 0).count(), inFlight, droppedCount.get());
    }

    /** Replaces best-effort enrichment only while the original report remains queue-owned. */
    public synchronized void enrich(PendingTelemetryPayload pending, JsonObject enriched) {
        if (closed.get() || !queue.contains(pending) || enriched == null) {
            return;
        }
        JsonObject original = pending.payload;
        int previousBytes = pending.reservedBytes();
        pending.payload = enriched.deepCopy();
        if (original.has("report_id")) {
            pending.payload.add("report_id", original.get("report_id"));
        }
        pending.reservedBytes = 0;
        int nextBytes = pending.reservedBytes();
        if (nextBytes > MAX_ROW_CHARS || queuedBytes - previousBytes + nextBytes > MAX_SPOOL_BYTES) {
            pending.payload = original;
            pending.reservedBytes = previousBytes;
            return;
        }
        queuedBytes += nextBytes - previousBytes;
        schedulePersistence();
    }

    private void loadFromDisk() {
        if (!Files.exists(spoolPath)) {
            return;
        }
        int limit = MAX_ENTRIES;
        // Config is loaded for runtime queues; injectable disk tests can run before NeoForge config load.
        try {
            limit = Math.clamp(TelemetryConfig.values().queueMaxSize(), 1, MAX_ENTRIES);
        } catch (IllegalStateException ignored) {
            // The hard limit still bounds offline tests and early lifecycle construction.
        }
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                new SpoolInputStream(Files.newInputStream(spoolPath)), java.nio.charset.StandardCharsets.UTF_8))) {
            readBoundedRows(reader, limit, line -> {
                try {
                    if (!hasBoundedNesting(line)) {
                        droppedCount.incrementAndGet();
                        return false;
                    }
                    PendingTelemetryPayload payload = GSON.fromJson(line, PendingTelemetryPayload.class);
                    if (payload == null || !payload.normalizeAfterLoad()
                            || payload.reservedBytes() > MAX_ROW_CHARS
                            || queuedBytes + payload.reservedBytes() > MAX_SPOOL_BYTES) {
                        droppedCount.incrementAndGet();
                        return false;
                    }
                    queue.addLast(payload);
                    queuedBytes += payload.reservedBytes();
                    return true;
                } catch (RuntimeException invalidRow) {
                    droppedCount.incrementAndGet();
                    return false;
                }
            });
            if (droppedCount.get() > 0) {
                LOGGER.warn("[Telemetry] Skipped {} invalid persisted reports.", droppedCount.get());
            }
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Queue load stopped by an I/O error or its 16 MiB scan limit; valid reports were retained.");
        }
    }

    static int readBoundedRows(java.io.Reader reader, int maximumEntries,
                               java.util.function.Predicate<String> accept) throws IOException {
        int limit = Math.clamp(maximumEntries, 0, MAX_ENTRIES);
        if (limit == 0) {
            return 0;
        }
        StringBuilder row = new StringBuilder(1024);
        int accepted = 0;
        boolean oversized = false;
        for (int scanned = 0; scanned < MAX_SPOOL_BYTES; scanned++) {
            int value = reader.read();
            if (value == -1) {
                if (!oversized && !row.isEmpty() && accept.test(row.toString())) {
                    accepted++;
                }
                return accepted;
            }
            if (value == '\n') {
                if (!oversized && !row.isEmpty() && accept.test(row.toString()) && ++accepted >= limit) {
                    return accepted;
                }
                row.setLength(0);
                oversized = false;
            } else if (!oversized) {
                if (row.length() < MAX_ROW_CHARS) {
                    row.append((char) value);
                } else {
                    row.setLength(0);
                    oversized = true;
                }
            }
        }
        return accepted;
    }

    static boolean hasBoundedNesting(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char value = json.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                }
            } else if (value == '"') {
                quoted = true;
            } else if (value == '{' || value == '[') {
                if (++depth > 64) {
                    return false;
                }
            } else if (value == '}' || value == ']') {
                if (--depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !quoted;
    }

    private static final class SpoolInputStream extends java.io.FilterInputStream {
        private long remaining = MAX_SPOOL_BYTES;

        private SpoolInputStream(java.io.InputStream input) {
            super(input);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                throw new java.io.EOFException("Spool scan limit");
            }
            int count = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (count > 0) {
                remaining -= count;
            }
            return count;
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
                snapshot = queue.stream().map(PendingTelemetryPayload::copy).toList();
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
            if (closed.get()) {
                synchronized (this) {
                    snapshot = queue.stream().map(PendingTelemetryPayload::copy).toList();
                }
            }
            return writeSnapshot(snapshot);
        }
    }

    private boolean writeSnapshot(List<PendingTelemetryPayload> snapshot) {
        try {
            Files.createDirectories(spoolPath.getParent());
        } catch (IOException ex) {
            LOGGER.warn("[Telemetry] Failed to create telemetry queue directory (I/O failure).");
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
            LOGGER.warn("[Telemetry] Failed to write telemetry queue snapshot (I/O failure).");
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
            LOGGER.warn("[Telemetry] Failed to publish telemetry queue snapshot (I/O failure).");
            return false;
        }
    }

    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<PendingTelemetryPayload> snapshot;
        synchronized (this) {
            snapshot = queue.stream().map(PendingTelemetryPayload::copy).toList();
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
        private volatile JsonObject payload;
        private transient int reservedBytes;
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
            this.payload = payload == null ? null : payload.deepCopy();
            if (this.payload != null && !this.payload.has("report_id")) {
                this.payload.addProperty("report_id", java.util.UUID.randomUUID().toString());
            }
            this.webhookUrl = webhookUrl;
            this.timeoutSeconds = timeoutSeconds;
            this.maxRetries = maxRetries;
            this.retryBaseDelayMs = baseDelay.toMillis();
            this.retryMaxDelayMs = maxDelay.toMillis();
            this.attempts = 0;
            this.createdAt = Instant.now();
        }

        private int reservedBytes() {
            if (reservedBytes == 0) {
                // Reserve space for attempt counters and timestamps added after the first write.
                reservedBytes = GSON.toJson(this).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 512;
            }
            return reservedBytes;
        }

        private PendingTelemetryPayload copy() {
            var copy = new PendingTelemetryPayload(type, payload, webhookUrl, timeoutSeconds, maxRetries,
                    Duration.ofMillis(retryBaseDelayMs), Duration.ofMillis(retryMaxDelayMs));
            copy.attempts = attempts;
            copy.createdAt = createdAt;
            copy.lastAttempt = lastAttempt;
            return copy;
        }

        private boolean normalizeAfterLoad() {
            if (type == null || type.isBlank() || type.length() > 128 || payload == null
                    || webhookUrl == null || webhookUrl.length() > 8192
                    || !com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient.isConfigured(webhookUrl)) {
                return false;
            }
            timeoutSeconds = Math.clamp(timeoutSeconds, 1, 60);
            maxRetries = Math.clamp(maxRetries, 0, 10);
            retryBaseDelayMs = Math.clamp(retryBaseDelayMs, 1L, 10_000L);
            retryMaxDelayMs = Math.clamp(retryMaxDelayMs, retryBaseDelayMs, 60_000L);
            attempts = Math.clamp(attempts, 0, 1_000_000);
            if (!payload.has("report_id")) {
                payload.addProperty("report_id", java.util.UUID.randomUUID().toString());
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return true;
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
                LOGGER.warn("[Telemetry] Queued report submission failed.");
                return false;
            }
        }

        public void incrementAttempts() {
            attempts = Math.min(1_000_000, attempts + 1);
            lastAttempt = Instant.now();
        }
    }

    public record TelemetryQueueStats(int pending, int failed, Instant lastSuccess, int retrying, int inFlight, int dropped) {
        /** Retains the original diagnostics constructor for existing integrations. */
        public TelemetryQueueStats(int pending, int failed, Instant lastSuccess) {
            this(pending, failed, lastSuccess, 0, 0, 0);
        }

        public Optional<Instant> lastSuccessOptional() {
            return Optional.ofNullable(lastSuccess);
        }
    }
}
