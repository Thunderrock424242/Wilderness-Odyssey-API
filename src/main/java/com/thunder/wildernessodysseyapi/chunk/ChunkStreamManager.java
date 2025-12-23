package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
            entry.setLastPersisted(warmPayload.copy());
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, warmPayload, true));
        }

        entry.setState(ChunkState.QUEUED);
        return ioController.loadChunk(pos).thenApply(payload -> {
            entry.setState(ChunkState.READY);
            CompoundTag resolved = payload.orElseGet(CompoundTag::new);
            entry.setLastPersisted(resolved.copy());
            return new ChunkLoadResult(pos, resolved, false);
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
        CompoundTag snapshot = payload.copy();
        synchronized (WARM_CACHE) {
            WARM_CACHE.put(pos, snapshot.copy());
        }
        DirtySegmentSet diff = DirtySegmentSet.diff(entry.getLastPersisted(), snapshot);
        entry.markDirty(diff);
        ioController.enqueueSave(pos, snapshot, diff, gameTime);
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

    public static void flushChunk(ChunkPos pos) {
        if (!config.enabled()) {
            return;
        }
        STATE.remove(pos);
        synchronized (HOT_CACHE) {
            HOT_CACHE.remove(pos);
        }
        synchronized (WARM_CACHE) {
            WARM_CACHE.remove(pos);
        }
        ioController.flushChunk(pos);
    }

    public static void flushAll() {
        if (!config.enabled()) {
            return;
        }
        ioController.flushAll();
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
        private volatile CompoundTag lastPersisted;
        private volatile DirtySegmentSet dirtySegments = DirtySegmentSet.none();

        void upsertTicket(ChunkTicketType type, long expiryTick) {
            tickets.put(type, new ChunkTicket(type, expiryTick));
        }

        void setState(ChunkState newState) {
            state = newState;
        }

        void touch(long gameTime) {
            lastTouched = gameTime;
        }

        CompoundTag getLastPersisted() {
            return lastPersisted;
        }

        void setLastPersisted(CompoundTag tag) {
            lastPersisted = tag;
            dirtySegments = DirtySegmentSet.none();
        }

        DirtySegmentSet consumeDirty() {
            DirtySegmentSet current = dirtySegments;
            dirtySegments = DirtySegmentSet.none();
            return current;
        }

        void markDirty(DirtySegmentSet diff) {
            dirtySegments = dirtySegments.mergedWith(diff);
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

        void enqueueSave(ChunkPos pos, CompoundTag payload, DirtySegmentSet dirtySegments, long gameTime) {
            pendingSaves.merge(
                    pos,
                    new PendingSave(payload, dirtySegments, gameTime + configSupplier.get().saveDebounceTicks()),
                    (existing, incoming) -> new PendingSave(
                            incoming.payload(),
                            existing.dirtySegments().mergedWith(incoming.dirtySegments()),
                            incoming.scheduledTick())
            );
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
            pendingSaves.entrySet().removeIf(entry -> {
                if (entry.getValue().scheduledTick() > gameTime) {
                    return false;
                }
                if (inFlight.get() >= values.maxParallelIo()) {
                    return false;
                }
                inFlight.incrementAndGet();
                ChunkPos pos = entry.getKey();
                PendingSave save = entry.getValue();
                AsyncTaskManager.submitIoTask("chunk-save-" + pos, () -> {
                            try {
                                ChunkStatusEntry statusEntry = STATE.get(pos);
                                if (statusEntry == null) {
                                    return Optional.empty();
                                }

                                DirtySegmentSet dirty = save.dirtySegments();
                                if (!dirty.hasEntries()) {
                                    return Optional.empty();
                                }

                                CompoundTag merged = dirty.isFullChunkDirty()
                                        ? save.payload().copy()
                                        : mergeDirtySegments(statusEntry.getLastPersisted(), save.payload(), dirty);

                                adapterSupplier.get().write(pos, merged);
                                statusEntry.setLastPersisted(merged.copy());
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
        private CompoundTag mergeDirtySegments(CompoundTag baseline, CompoundTag latest, DirtySegmentSet dirty) {
            CompoundTag merged = baseline == null ? new CompoundTag() : baseline.copy();

            for (String key : dirty.topLevelKeys()) {
                if (latest.contains(key)) {
                    merged.put(key, latest.get(key).copy());
                } else {
                    merged.remove(key);
                }
            }

            if (dirty.sectionYLevels().isEmpty()) {
                return merged;
            }

            ListTag sections = merged.contains("sections", Tag.TAG_LIST) ? merged.getList("sections", Tag.TAG_COMPOUND) : new ListTag();
            Map<Integer, CompoundTag> latestSections = DirtySegmentSet.indexSections(latest);

            sections.removeIf(tag -> {
                if (!(tag instanceof CompoundTag section) || !section.contains("Y")) {
                    return false;
                }
                int y = section.getByte("Y");
                if (!dirty.sectionYLevels().contains(y)) {
                    return false;
                }
                return true;
            });

            for (Integer y : dirty.sectionYLevels()) {
                CompoundTag updatedSection = latestSections.get(y);
                if (updatedSection != null) {
                    sections.add(updatedSection.copy());
                }
            }

            merged.put("sections", sections);
            return merged;
        void flushChunk(ChunkPos pos) {
            PendingSave save = pendingSaves.remove(pos);
            if (save != null) {
                writeImmediately(pos, save.payload());
            }
        }

        void flushAll() {
            pendingSaves.forEach(this::writeImmediately);
            pendingSaves.clear();
        }

        private void writeImmediately(ChunkPos pos, CompoundTag payload) {
            ChunkStorageAdapter adapter = adapterSupplier.get();
            if (adapter == null) {
                return;
            }
            try {
                adapter.write(pos, payload);
            } catch (Exception e) {
                ModConstants.LOGGER.error("[ChunkStream] Failed to flush chunk {}", pos, e);
            }
        }
    }

    private record PendingSave(CompoundTag payload, DirtySegmentSet dirtySegments, long scheduledTick) {
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
