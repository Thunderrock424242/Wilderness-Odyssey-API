package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates chunk ticket lifecycle, caching, and async I/O.
 */
public final class ChunkStreamManager {
    private static final ConcurrentMap<ChunkPos, ChunkStatusEntry> STATE = new ConcurrentHashMap<>();
    private static final LinkedHashMap<ChunkPos, CompoundTag> WARM_CACHE = new LinkedHashMap<>(128, 0.75f, true);
    private static final LinkedHashMap<ChunkPos, Boolean> HOT_CACHE = new LinkedHashMap<>(128, 0.75f, true);

    private static ChunkStreamingConfig.ChunkConfigValues config = ChunkStreamingConfig.values();
    private static ChunkStorageAdapter storageAdapter;
    private static ChunkIoController ioController = new ChunkIoController(() -> config, () -> storageAdapter);

    private ChunkStreamManager() {
    }

    public static synchronized void initialize(ChunkStreamingConfig.ChunkConfigValues values, ChunkStorageAdapter adapter) {
        config = values;
        storageAdapter = adapter;
        STATE.clear();
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter);
        ModConstants.LOGGER.info("[ChunkStream] Initialized (hot cache: {}, warm cache: {}, debounce: {} ticks)",
                config.hotCacheLimit(), config.warmCacheLimit(), config.saveDebounceTicks());
    }

    public static synchronized void shutdown() {
        STATE.clear();
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter);
    }

    public static CompletableFuture<ChunkLoadResult> requestChunk(ChunkPos pos, ChunkTicketType ticketType, long gameTime) {
        if (!config.enabled()) {
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, null, false));
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.upsertTicket(ticketType, gameTime + ticketType.resolveTtl(config));
        entry.transitionTo(ChunkState.QUEUED);
        promoteHot(pos);

        CompoundTag warmPayload;
        synchronized (WARM_CACHE) {
            warmPayload = WARM_CACHE.remove(pos);
        }
        if (warmPayload != null) {
            entry.transitionTo(ChunkState.READY);
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, warmPayload, true));
        }

        entry.transitionTo(ChunkState.LOADING);
        ioController.cancelPendingSave(pos);
        return ioController.loadChunk(pos).thenApply(payload -> {
            entry.transitionTo(ChunkState.READY);
            return new ChunkLoadResult(pos, payload.orElseGet(CompoundTag::new), false);
        });
    }

    public static void scheduleSave(ChunkPos pos, CompoundTag payload, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.transitionTo(ChunkState.ACTIVE);
        cacheWarm(pos, payload);
        ioController.enqueueSave(pos, payload, gameTime);
    }

    public static void markActive(ChunkPos pos, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.transitionTo(ChunkState.ACTIVE);
        promoteHot(pos);
        removeWarm(pos);
    }

    public static void tick(long gameTime) {
        if (!config.enabled()) {
            return;
        }
        expireTickets(gameTime);
        ioController.tick(gameTime);
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
        List<ChunkPos> expired = new ArrayList<>();
        STATE.forEach((pos, entry) -> {
            entry.tickets.entrySet().removeIf(ticket -> ticket.getValue().isExpired(gameTime));
            if (entry.tickets.isEmpty()) {
                entry.transitionTo(ChunkState.UNLOADING);
                expired.add(pos);
            }
        });
        for (ChunkPos pos : expired) {
            cleanupChunk(pos, "ticket-expired", true);
        }
    }

    private static void promoteHot(ChunkPos pos) {
        synchronized (HOT_CACHE) {
            HOT_CACHE.put(pos, Boolean.TRUE);
            trimHotCache();
        }
    }

    private static void demoteToWarm(ChunkPos pos) {
        ChunkStatusEntry entry = STATE.get(pos);
        if (entry != null) {
            entry.transitionTo(ChunkState.READY);
        }
        cacheWarm(pos, new CompoundTag());
    }

    private static void cacheWarm(ChunkPos pos, CompoundTag payload) {
        if (!config.splitWarmCache() || config.warmCacheLimit() <= 0) {
            return;
        }
        synchronized (WARM_CACHE) {
            WARM_CACHE.put(pos, payload.copy());
            trimWarmCache();
        }
    }

    private static void removeWarm(ChunkPos pos) {
        synchronized (WARM_CACHE) {
            WARM_CACHE.remove(pos);
        }
    }

    private static void trimHotCache() {
        int limit = config.hotCacheLimit();
        synchronized (HOT_CACHE) {
            while (HOT_CACHE.size() > limit) {
                Iterator<Map.Entry<ChunkPos, Boolean>> iterator = HOT_CACHE.entrySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                ChunkPos pos = iterator.next().getKey();
                iterator.remove();
                if (config.splitWarmCache()) {
                    demoteToWarm(pos);
                } else {
                    cleanupChunk(pos, "hot-cache-overflow", true);
                }
            }
        }
    }

    private static void trimWarmCache() {
        int limit = config.warmCacheLimit();
        if (!config.splitWarmCache() || limit <= 0) {
            synchronized (WARM_CACHE) {
                if (!config.splitWarmCache()) {
                    WARM_CACHE.clear();
                }
            }
            return;
        }
        synchronized (WARM_CACHE) {
            while (WARM_CACHE.size() > limit) {
                ChunkPos eviction = selectWarmEvictionCandidate();
                if (eviction == null) {
                    break;
                }
                removeWarm(eviction);
                cleanupChunk(eviction, "warm-cache-capacity", false);
            }
        }
    }

    private static ChunkPos selectWarmEvictionCandidate() {
        ChunkPos candidate = null;
        int lowestPriority = Integer.MAX_VALUE;
        for (Map.Entry<ChunkPos, CompoundTag> entry : WARM_CACHE.entrySet()) {
            ChunkStatusEntry statusEntry = STATE.get(entry.getKey());
            int priority = statusEntry == null ? 0 : statusEntry.highestPriority();
            if (priority < lowestPriority) {
                lowestPriority = priority;
                candidate = entry.getKey();
            }
        }
        return candidate;
    }

    private static void cleanupChunk(ChunkPos pos, String reason, boolean removeState) {
        synchronized (HOT_CACHE) {
            HOT_CACHE.remove(pos);
        }
        removeWarm(pos);
        dropChunkBuffers(pos);
        ioController.cancel(pos);
        if (removeState) {
            STATE.remove(pos);
        }
        ModConstants.LOGGER.debug("[ChunkStream] Evicted chunk {} ({})", pos, reason);
    }

    private static void dropChunkBuffers(ChunkPos pos) {
        // Placeholder for future mesh/light buffer cleanup hooks.
        ModConstants.LOGGER.trace("[ChunkStream] Dropping cached buffers for {}", pos);
    }

    private static final class ChunkStatusEntry {
        private volatile ChunkState state = ChunkState.UNLOADED;
        private final Map<ChunkTicketType, ChunkTicket> tickets = new EnumMap<>(ChunkTicketType.class);
        private volatile long lastTouched;

        void upsertTicket(ChunkTicketType type, long expiryTick) {
            tickets.put(type, new ChunkTicket(type, expiryTick));
        }

        void transitionTo(ChunkState newState) {
            boolean allowDemotion = state == ChunkState.ACTIVE && newState == ChunkState.READY;
            if (newState == ChunkState.UNLOADED || newState.ordinal() >= state.ordinal() || allowDemotion) {
                state = newState;
            }
        }

        int highestPriority() {
            return tickets.values().stream()
                    .map(ChunkTicket::type)
                    .mapToInt(ChunkTicketType::priority)
                    .max()
                    .orElse(0);
        }

        void touch(long gameTime) {
            lastTouched = gameTime;
        }
    }

    private static class ChunkIoController {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final Map<ChunkPos, PendingSave> pendingSaves = new ConcurrentHashMap<>();
        private final Map<ChunkPos, CompletableFuture<Optional<CompoundTag>>> loadTasks = new ConcurrentHashMap<>();
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
            loadTasks.put(pos, payloadFuture);
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
                        loadTasks.remove(pos);
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

        void cancelPendingSave(ChunkPos pos) {
            pendingSaves.remove(pos);
        }

        void cancel(ChunkPos pos) {
            cancelPendingSave(pos);
            CompletableFuture<Optional<CompoundTag>> loadFuture = loadTasks.remove(pos);
            if (loadFuture != null && !loadFuture.isDone()) {
                loadFuture.cancel(true);
            }
        }

        void tick(long gameTime) {
            ChunkStreamingConfig.ChunkConfigValues values = configSupplier.get();
            pendingSaves.entrySet().removeIf(entry -> {
                if (entry.getValue().scheduledTick() > gameTime) {
                    return false;
                }
                if (inFlight.get() >= values.maxParallelIo()) {
                    return false;
                }
                inFlight.incrementAndGet();
                ChunkPos pos = entry.getKey();
                CompoundTag tag = entry.getValue().payload();
                AsyncTaskManager.submitIoTask("chunk-save-" + pos, () -> {
                            try {
                                adapterSupplier.get().write(pos, tag);
                            } catch (Exception e) {
                                ModConstants.LOGGER.error("[ChunkStream] Failed to write chunk {}", pos, e);
                            }
                            return Optional.empty();
                        })
                        .whenComplete((ignored, throwable) -> inFlight.decrementAndGet());
                return true;
            });
        }

        int inFlightLoads() {
            return Math.max(0, inFlight.get());
        }

        int pendingSaves() {
            return pendingSaves.size();
        }
    }

    private record PendingSave(CompoundTag payload, long scheduledTick) {
    }
}
