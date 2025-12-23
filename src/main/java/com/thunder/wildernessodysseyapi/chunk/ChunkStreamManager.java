package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Coordinates chunk ticket lifecycle, caching, and async I/O.
 */
public final class ChunkStreamManager {
    private static final ConcurrentMap<ChunkPos, ChunkStatusEntry> STATE = new ConcurrentHashMap<>();
    private static final LinkedHashMap<ChunkPos, CompoundTag> WARM_CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ChunkPos, CompoundTag> eldest) {
            return size() > config.warmCacheLimit();
        }
    };
    private static final LinkedHashMap<ChunkPos, Boolean> HOT_CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ChunkPos, Boolean> eldest) {
            boolean evict = size() > config.hotCacheLimit();
            if (evict) {
                demoteToWarm(eldest.getKey());
            }
            return evict;
        }
    };

    private static ChunkStreamingConfig.ChunkConfigValues config = ChunkStreamingConfig.values();
    private static ChunkStorageAdapter storageAdapter;
    private static ChunkIoController ioController = new ChunkIoController(() -> config, () -> storageAdapter);
    private static ScheduledExecutorService writeScheduler;
    private static ScheduledFuture<?> writeFlushTask;
    private static final AtomicLong lastGameTime = new AtomicLong();

    private ChunkStreamManager() {
    }

    public static synchronized void initialize(ChunkStreamingConfig.ChunkConfigValues values, ChunkStorageAdapter adapter) {
        config = values;
        storageAdapter = adapter;
        STATE.clear();
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter);
        scheduleWriteFlush();
        ModConstants.LOGGER.info("[ChunkStream] Initialized (hot cache: {}, warm cache: {}, debounce: {} ticks)",
                config.hotCacheLimit(), config.warmCacheLimit(), config.saveDebounceTicks());
    }

    public static synchronized void shutdown() {
        cancelScheduler();
        STATE.clear();
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter);
    }

    public static CompletableFuture<ChunkLoadResult> requestChunk(ChunkPos pos, ChunkTicketType ticketType, long gameTime) {
        if (!config.enabled()) {
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, null, false));
        }
        lastGameTime.set(gameTime);
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.upsertTicket(ticketType, gameTime + ticketType.resolveTtl(config));
        promoteHot(pos);

        CompoundTag warmPayload;
        synchronized (WARM_CACHE) {
            warmPayload = WARM_CACHE.remove(pos);
        }
        if (warmPayload != null) {
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, warmPayload, true));
        }

        entry.setState(ChunkState.QUEUED);
        return ioController.loadChunk(pos).thenApply(payload -> {
            entry.setState(ChunkState.READY);
            return new ChunkLoadResult(pos, payload.orElseGet(CompoundTag::new), false);
        });
    }

    public static void scheduleSave(ChunkPos pos, CompoundTag payload, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        lastGameTime.set(gameTime);
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.setState(ChunkState.ACTIVE);
        synchronized (WARM_CACHE) {
            WARM_CACHE.put(pos, payload.copy());
        }
        ioController.enqueueSave(pos, payload, gameTime);
    }

    public static void markActive(ChunkPos pos, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        lastGameTime.set(gameTime);
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.setState(ChunkState.ACTIVE);
        promoteHot(pos);
    }

    public static void tick(long gameTime) {
        if (!config.enabled()) {
            return;
        }
        lastGameTime.set(gameTime);
        expireTickets(gameTime);
    }

    /**
     * Immediately flushes all pending chunk saves, ignoring debounce windows. Intended for world saves or shutdown.
     */
    public static void flushAll(long gameTime) {
        if (!config.enabled()) {
            return;
        }
        lastGameTime.set(gameTime);
        ioController.flushAll(gameTime).join();
    }

    public static ChunkStreamStats snapshot() {
        return new ChunkStreamStats(
                config.enabled(),
                STATE.size(),
                HOT_CACHE.size(),
                WARM_CACHE.size(),
                ioController.inFlightLoads(),
                ioController.pendingSaves()
        );
    }

    private static void expireTickets(long gameTime) {
        STATE.forEach((pos, entry) -> {
            entry.tickets.entrySet().removeIf(ticket -> ticket.getValue().isExpired(gameTime));
            if (entry.tickets.isEmpty()) {
                entry.setState(ChunkState.UNLOADING);
                synchronized (WARM_CACHE) {
                    CompoundTag cached = WARM_CACHE.remove(pos);
                    if (cached != null) {
                        ModConstants.LOGGER.debug("[ChunkStream] Evicting warm chunk {} after ticket expiry.", pos);
                    }
                }
                synchronized (HOT_CACHE) {
                    HOT_CACHE.remove(pos);
                }
                STATE.remove(pos);
            }
        });
    }

    private static void promoteHot(ChunkPos pos) {
        synchronized (HOT_CACHE) {
            HOT_CACHE.put(pos, Boolean.TRUE);
        }
    }

    private static void demoteToWarm(ChunkPos pos) {
        synchronized (WARM_CACHE) {
            if (!WARM_CACHE.containsKey(pos)) {
                WARM_CACHE.put(pos, new CompoundTag());
            }
        }
    }

    private static final class ChunkStatusEntry {
        private volatile ChunkState state = ChunkState.UNLOADED;
        private final Map<ChunkTicketType, ChunkTicket> tickets = new EnumMap<>(ChunkTicketType.class);
        private volatile long lastTouched;

        void upsertTicket(ChunkTicketType type, long expiryTick) {
            tickets.put(type, new ChunkTicket(type, expiryTick));
        }

        void setState(ChunkState newState) {
            state = newState;
        }

        void touch(long gameTime) {
            lastTouched = gameTime;
        }
    }

    private static class ChunkIoController {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final Map<ChunkPos, PendingSave> pendingSaves = new ConcurrentHashMap<>();
        private final java.util.function.Supplier<ChunkStreamingConfig.ChunkConfigValues> configSupplier;
        private final java.util.function.Supplier<ChunkStorageAdapter> adapterSupplier;

        ChunkIoController(java.util.function.Supplier<ChunkStreamingConfig.ChunkConfigValues> configSupplier,
                          java.util.function.Supplier<ChunkStorageAdapter> adapterSupplier) {
            this.configSupplier = configSupplier;
            this.adapterSupplier = adapterSupplier;
        }

        CompletableFuture<Optional<CompoundTag>> loadChunk(ChunkPos pos) {
            ChunkStreamingConfig.ChunkConfigValues values = configSupplier.get();
            if (inFlight.incrementAndGet() > values.maxParallelIo()) {
                inFlight.decrementAndGet();
                return CompletableFuture.completedFuture(Optional.empty());
            }

            CompletableFuture<Optional<CompoundTag>> payloadFuture = new CompletableFuture<>();
            AsyncTaskManager.submitIoTask("chunk-load-" + pos, () -> {
                        try {
                            ChunkStorageAdapter adapter = adapterSupplier.get();
                            if (adapter == null) {
                                payloadFuture.complete(Optional.empty());
                                return Optional.empty();
                            }
                            Optional<CompoundTag> payload = adapter.read(pos);
                            payloadFuture.complete(payload);
                        } catch (Exception e) {
                            payloadFuture.completeExceptionally(e);
                        }
                        return Optional.empty();
                    })
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null && !payloadFuture.isDone()) {
                            payloadFuture.completeExceptionally(throwable);
                        }
                        inFlight.decrementAndGet();
                    });

            return payloadFuture.exceptionally(ex -> {
                ModConstants.LOGGER.error("[ChunkStream] Failed to read chunk {}", pos, ex);
                return Optional.empty();
            });
        }

        void enqueueSave(ChunkPos pos, CompoundTag payload, long gameTime) {
            pendingSaves.put(pos, new PendingSave(payload, gameTime + configSupplier.get().saveDebounceTicks()));
        }

        void tick(long gameTime) {
            ChunkStreamingConfig.ChunkConfigValues values = configSupplier.get();
            pendingSaves.entrySet().removeIf(entry -> tryFlushEntry(entry.getKey(), entry.getValue(), values, gameTime));
        }

        CompletableFuture<Void> flushAll(long gameTime) {
            Map<ChunkPos, PendingSave> snapshot = pendingSaves.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            pendingSaves.clear();
            if (snapshot.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.allOf(snapshot.entrySet().stream()
                    .map(entry -> {
                        inFlight.incrementAndGet();
                        return submitWrite(entry.getKey(), entry.getValue().payload());
                    })
                    .toArray(CompletableFuture[]::new));
        }

        int inFlightLoads() {
            return Math.max(0, inFlight.get());
        }

        int pendingSaves() {
            return pendingSaves.size();
        }

        private boolean tryFlushEntry(ChunkPos pos, PendingSave pending, ChunkStreamingConfig.ChunkConfigValues values, long gameTime) {
            if (pending.scheduledTick() > gameTime) {
                return false;
            }
            if (inFlight.get() >= values.maxParallelIo()) {
                return false;
            }
            inFlight.incrementAndGet();
            submitWrite(pos, pending.payload());
            return true;
        }

        private CompletableFuture<Void> submitWrite(ChunkPos pos, CompoundTag tag) {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            AsyncTaskManager.submitIoTask("chunk-save-" + pos, () -> {
                        try {
                            ChunkStorageAdapter adapter = adapterSupplier.get();
                            if (adapter == null) {
                                completion.complete(null);
                                return Optional.empty();
                            }
                            adapter.write(pos, tag);
                        } catch (Exception e) {
                            ModConstants.LOGGER.error("[ChunkStream] Failed to write chunk {}", pos, e);
                            completion.completeExceptionally(e);
                        }
                        return Optional.empty();
                    })
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null && !completion.isDone()) {
                            completion.completeExceptionally(throwable);
                        } else if (!completion.isDone()) {
                            completion.complete(null);
                        }
                        inFlight.decrementAndGet();
                    });
            return completion;
        }
    }

    private record PendingSave(CompoundTag payload, long scheduledTick) {
    }

    private static synchronized void scheduleWriteFlush() {
        cancelScheduler();
        if (!config.enabled()) {
            return;
        }
        writeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wildernessodyssey-chunk-writer");
            t.setDaemon(true);
            return t;
        });
        long intervalTicks = Math.max(1, config.writeFlushIntervalTicks());
        long intervalMs = intervalTicks * 50L;
        writeFlushTask = writeScheduler.scheduleAtFixedRate(() -> {
            try {
                ioController.tick(lastGameTime.get());
            } catch (Exception e) {
                ModConstants.LOGGER.error("[ChunkStream] Scheduled write flush failed", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private static synchronized void cancelScheduler() {
        if (writeFlushTask != null) {
            writeFlushTask.cancel(false);
            writeFlushTask = null;
        }
        if (writeScheduler != null) {
            writeScheduler.shutdownNow();
            writeScheduler = null;
        }
    }
}
