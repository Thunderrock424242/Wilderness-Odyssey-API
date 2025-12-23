package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.io.BufferPool;
import com.thunder.wildernessodysseyapi.io.IoExecutors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

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
    private static ChunkIoController ioController = new ChunkIoController(() -> config, () -> storageAdapter, null);

    private ChunkStreamManager() {
    }

    public static synchronized void initialize(ChunkStreamingConfig.ChunkConfigValues values, ChunkStorageAdapter adapter) {
        config = values;
        storageAdapter = adapter;
        BufferPool.configure(values);
        STATE.clear();
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter, null);
        ModConstants.LOGGER.info("[ChunkStream] Initialized (hot cache: {}, warm cache: {}, debounce: {} ticks)",
                config.hotCacheLimit(), config.warmCacheLimit(), config.saveDebounceTicks());
    }

    public static synchronized void shutdown() {
        STATE.clear();
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter, null);
    }

    public static CompletableFuture<ChunkLoadResult> requestChunk(ResourceKey<Level> dimension, ChunkPos pos, ChunkTicketType ticketType, long gameTime) {
        return requestChunkInternal(dimension, pos, ticketType, gameTime);
    }

    public static CompletableFuture<ChunkLoadResult> requestChunk(ChunkPos pos, ChunkTicketType ticketType, long gameTime) {
        return requestChunkInternal(null, pos, ticketType, gameTime);
    }

    private static CompletableFuture<ChunkLoadResult> requestChunkInternal(ResourceKey<Level> dimension, ChunkPos pos, ChunkTicketType ticketType, long gameTime) {
        if (!config.enabled()) {
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, null, false));
        }
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
        return ioController.loadChunk(pos, dimension).thenApply(payload -> {
            entry.setState(ChunkState.READY);
            return new ChunkLoadResult(pos, payload.orElseGet(CompoundTag::new), false);
        });
    }

    public static void scheduleSave(ResourceKey<Level> dimension, ChunkPos pos, CompoundTag payload, long gameTime) {
        scheduleSaveInternal(dimension, pos, payload, gameTime);
    }

    public static void scheduleSave(ChunkPos pos, CompoundTag payload, long gameTime) {
        scheduleSaveInternal(null, pos, payload, gameTime);
    }

    private static void scheduleSaveInternal(ResourceKey<Level> dimension, ChunkPos pos, CompoundTag payload, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.setState(ChunkState.ACTIVE);
        synchronized (WARM_CACHE) {
            WARM_CACHE.put(pos, payload.copy());
        }
        ioController.enqueueSave(pos, payload, gameTime, dimension);
    }

    public static void markActive(ChunkPos pos, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.setState(ChunkState.ACTIVE);
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
        private final ResourceKey<Level> dimension;

        ChunkIoController(java.util.function.Supplier<ChunkStreamingConfig.ChunkConfigValues> configSupplier,
                          java.util.function.Supplier<ChunkStorageAdapter> adapterSupplier,
                          ResourceKey<Level> dimension) {
            this.configSupplier = configSupplier;
            this.adapterSupplier = adapterSupplier;
            this.dimension = dimension;
        }

        CompletableFuture<Optional<CompoundTag>> loadChunk(ChunkPos pos, ResourceKey<Level> requestedDimension) {
            ChunkStreamingConfig.ChunkConfigValues values = configSupplier.get();
            if (inFlight.incrementAndGet() > values.maxParallelIo()) {
                inFlight.decrementAndGet();
                return CompletableFuture.completedFuture(Optional.empty());
            }

            CompletableFuture<Optional<CompoundTag>> payloadFuture = new CompletableFuture<>();
            IoExecutors.submit(requestedDimension != null ? requestedDimension : dimension, "chunk-load-" + pos, () -> {
                try {
                    ChunkStorageAdapter adapter = adapterSupplier.get();
                    if (adapter == null) {
                        payloadFuture.complete(Optional.empty());
                        return;
                    }
                    Optional<CompoundTag> payload = adapter.read(pos);
                    payloadFuture.complete(payload);
                } catch (Exception e) {
                    payloadFuture.completeExceptionally(e);
                }
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

        void enqueueSave(ChunkPos pos, CompoundTag payload, long gameTime, ResourceKey<Level> requestedDimension) {
            pendingSaves.put(pos, new PendingSave(payload, gameTime + configSupplier.get().saveDebounceTicks(),
                    requestedDimension != null ? requestedDimension : dimension));
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
                IoExecutors.submit(entry.getValue().dimension(), "chunk-save-" + pos, () -> {
                    try {
                        adapterSupplier.get().write(pos, tag);
                    } catch (Exception e) {
                        ModConstants.LOGGER.error("[ChunkStream] Failed to write chunk {}", pos, e);
                    }
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

    private record PendingSave(CompoundTag payload, long scheduledTick, ResourceKey<Level> dimension) {
    }
}
