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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates chunk ticket lifecycle, caching, and async I/O.
 */
public final class ChunkStreamManager {
    private static final ConcurrentMap<ChunkPos, ChunkStatusEntry> STATE = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong WARM_CACHE_HITS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong WARM_CACHE_MISSES = new java.util.concurrent.atomic.AtomicLong();
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

    private ChunkStreamManager() {
    }

    public static synchronized void initialize(ChunkStreamingConfig.ChunkConfigValues values, ChunkStorageAdapter adapter) {
        config = values;
        storageAdapter = adapter;
        STATE.clear();
        WARM_CACHE_HITS.set(0L);
        WARM_CACHE_MISSES.set(0L);
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter);
        ModConstants.LOGGER.info("[ChunkStream] Initialized (hot cache: {}, warm cache: {}, debounce: {} ticks)",
                config.hotCacheLimit(), config.warmCacheLimit(), config.saveDebounceTicks());
    }

    public static synchronized void shutdown() {
        STATE.clear();
        WARM_CACHE_HITS.set(0L);
        WARM_CACHE_MISSES.set(0L);
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
        entry.upsertTicket(pos, ticketType, gameTime + ticketType.resolveTtl(config));
        promoteHot(pos);

        CompoundTag warmPayload;
        synchronized (WARM_CACHE) {
            warmPayload = WARM_CACHE.remove(pos);
        }
        if (warmPayload != null) {
            WARM_CACHE_HITS.incrementAndGet();
            if (ModConstants.LOGGER.isTraceEnabled()) {
                ModConstants.LOGGER.trace("[ChunkStream][{}] Served from warm cache.", pos);
            }
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, warmPayload, true));
        }
        WARM_CACHE_MISSES.incrementAndGet();

        entry.setState(pos, ChunkState.QUEUED);
        return ioController.loadChunk(pos, () -> entry.setState(pos, ChunkState.LOADING)).thenApply(payload -> {
            entry.setState(pos, ChunkState.READY);
            return new ChunkLoadResult(pos, payload.orElseGet(CompoundTag::new), false);
        });
    }

    public static void scheduleSave(ChunkPos pos, CompoundTag payload, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.setState(pos, ChunkState.ACTIVE);
        synchronized (WARM_CACHE) {
            WARM_CACHE.put(pos, payload.copy());
        }
        ioController.enqueueSave(pos, payload, gameTime);
    }

    public static void markActive(ChunkPos pos, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.setState(pos, ChunkState.ACTIVE);
        promoteHot(pos);
    }

    public static void tick(long gameTime) {
        if (!config.enabled()) {
            return;
        }
        expireTickets(gameTime);
        ioController.tick(gameTime);
    }

    public static ChunkStreamStats snapshot() {
        Map<ChunkState, Integer> stateCounts = new EnumMap<>(ChunkState.class);
        Map<ChunkTicketType, Integer> ticketCounts = new EnumMap<>(ChunkTicketType.class);
        java.util.concurrent.atomic.AtomicInteger totalTickets = new java.util.concurrent.atomic.AtomicInteger();

        STATE.forEach((pos, entry) -> {
            stateCounts.merge(entry.state, 1, Integer::sum);
            for (ChunkTicket ticket : entry.tickets.values()) {
                ticketCounts.merge(ticket.type(), 1, Integer::sum);
                totalTickets.incrementAndGet();
            }
        });

        return new ChunkStreamStats(
                config.enabled(),
                STATE.size(),
                HOT_CACHE.size(),
                WARM_CACHE.size(),
                ioController.inFlightLoads(),
                ioController.pendingSaves(),
                stateCounts,
                ticketCounts,
                totalTickets.get(),
                ioController.pendingSaves(),
                WARM_CACHE_HITS.get(),
                WARM_CACHE_MISSES.get()
        );
    }

    private static void expireTickets(long gameTime) {
        STATE.forEach((pos, entry) -> {
            entry.tickets.entrySet().removeIf(ticket -> {
                boolean expired = ticket.getValue().isExpired(gameTime);
                if (expired && ModConstants.LOGGER.isTraceEnabled()) {
                    ModConstants.LOGGER.trace("[ChunkStream][{}] Ticket {} expired at tick {}.", pos, ticket.getKey(), gameTime);
                }
                return expired;
            });
            if (entry.tickets.isEmpty()) {
                entry.setState(pos, ChunkState.UNLOADING);
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

        void upsertTicket(ChunkPos pos, ChunkTicketType type, long expiryTick) {
            tickets.put(type, new ChunkTicket(type, expiryTick));
            if (ModConstants.LOGGER.isTraceEnabled()) {
                ModConstants.LOGGER.trace("[ChunkStream][{}] Upserted ticket {} expiring at tick {}.", pos, type, expiryTick);
            }
        }

        void setState(ChunkPos pos, ChunkState newState) {
            if (state == newState) {
                return;
            }
            ChunkState previous = state;
            state = newState;
            if (ModConstants.LOGGER.isTraceEnabled()) {
                ModConstants.LOGGER.trace("[ChunkStream][{}] State {} -> {}.", pos, previous, newState);
            }
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

        CompletableFuture<Optional<CompoundTag>> loadChunk(ChunkPos pos, Runnable onStart) {
            ChunkStreamingConfig.ChunkConfigValues values = configSupplier.get();
            if (inFlight.incrementAndGet() > values.maxParallelIo()) {
                inFlight.decrementAndGet();
                return CompletableFuture.completedFuture(Optional.empty());
            }

            CompletableFuture<Optional<CompoundTag>> payloadFuture = new CompletableFuture<>();
            if (onStart != null) {
                try {
                    onStart.run();
                } catch (Exception e) {
                    ModConstants.LOGGER.error("[ChunkStream] Failed to mark chunk {} as loading.", pos, e);
                }
            }
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
