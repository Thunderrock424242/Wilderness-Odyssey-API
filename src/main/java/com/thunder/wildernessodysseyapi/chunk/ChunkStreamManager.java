package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.io.BufferPool;
import com.thunder.wildernessodysseyapi.io.IoExecutors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

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
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private static final AtomicLong WARM_CACHE_HITS = new AtomicLong();
    private static final AtomicLong WARM_CACHE_MISSES = new AtomicLong();
    private static ChunkSliceCache sliceCache = new ChunkSliceCache(384);

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
    private static ScheduledExecutorService writeScheduler;
    private static ScheduledFuture<?> writeFlushTask;
    private static final AtomicLong lastGameTime = new AtomicLong();

    private ChunkStreamManager() {
    }

    public static synchronized void initialize(ChunkStreamingConfig.ChunkConfigValues values, ChunkStorageAdapter adapter) {
        config = values;
        storageAdapter = adapter;
        BufferPool.configure(values);
        ChunkTickThrottler.configure(values);
        sliceCache = new ChunkSliceCache(values.sliceInternLimit());
        sliceCache.reset();
        STATE.clear();
        WARM_CACHE_HITS.set(0L);
        WARM_CACHE_MISSES.set(0L);
        WARM_CACHE.clear();
        HOT_CACHE.clear();
        ioController = new ChunkIoController(() -> config, () -> storageAdapter, null);
        scheduleWriteFlush();
        ModConstants.LOGGER.info("[ChunkStream] Initialized (hot cache: {}, warm cache: {}, debounce: {} ticks)",
                config.hotCacheLimit(), config.warmCacheLimit(), config.saveDebounceTicks());
    }

    public static synchronized void shutdown() {
        cancelScheduler();
        STATE.clear();
        WARM_CACHE_HITS.set(0L);
        WARM_CACHE_MISSES.set(0L);
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
        lastGameTime.set(gameTime);
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.upsertTicket(pos, ticketType, gameTime + ticketType.resolveTtl(config));
        entry.transitionTo(pos, ChunkState.QUEUED);
        promoteHot(pos);

        CompoundTag warmPayload;
        synchronized (WARM_CACHE) {
            warmPayload = WARM_CACHE.remove(pos);
        }
        if (warmPayload != null) {
            WARM_CACHE_HITS.incrementAndGet();
            entry.transitionTo(pos, ChunkState.READY);
            entry.setLastPersisted(warmPayload.copy());
            return CompletableFuture.completedFuture(new ChunkLoadResult(pos, warmPayload, true));
        }
        WARM_CACHE_MISSES.incrementAndGet();

        entry.transitionTo(pos, ChunkState.LOADING);
        ioController.cancelPendingSave(pos);
        return ioController.loadChunk(pos, dimension).thenApply(payload -> {
            CompoundTag resolved = payload.map(tag -> sliceCache.dedupe(pos, tag)).orElseGet(CompoundTag::new);
            entry.setLastPersisted(resolved.copy());
            entry.transitionTo(pos, ChunkState.READY);
            return new ChunkLoadResult(pos, resolved, false);
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
        CompoundTag sanitized = sliceCache.dedupe(pos, payload.copy());
        lastGameTime.set(gameTime);
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.transitionTo(pos, ChunkState.ACTIVE);
        cacheWarm(pos, sanitized);
        DirtySegmentSet diff = DirtySegmentSet.diff(entry.getLastPersisted(), sanitized);
        entry.markDirty(diff);
        ioController.enqueueSave(pos, sanitized, diff, gameTime, dimension);
    }

    public static void markActive(ChunkPos pos, long gameTime) {
        if (!config.enabled()) {
            return;
        }
        lastGameTime.set(gameTime);
        ChunkStatusEntry entry = STATE.computeIfAbsent(pos, ignored -> new ChunkStatusEntry());
        entry.touch(gameTime);
        entry.transitionTo(pos, ChunkState.ACTIVE);
        promoteHot(pos);
        removeWarm(pos);
    }

    public static void tick(long gameTime) {
        if (!config.enabled()) {
            return;
        }
        lastGameTime.set(gameTime);
        expireTickets(gameTime);
        ioController.tick(gameTime);
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
        Map<ChunkState, Integer> stateCounts = new EnumMap<>(ChunkState.class);
        Map<ChunkTicketType, Integer> ticketCounts = new EnumMap<>(ChunkTicketType.class);
        AtomicInteger totalTickets = new AtomicInteger();

        STATE.forEach((pos, entry) -> {
            stateCounts.merge(entry.state, 1, Integer::sum);
            entry.tickets.values().forEach(ticket -> {
                ticketCounts.merge(ticket.type(), 1, Integer::sum);
                totalTickets.incrementAndGet();
            });
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
        List<ChunkPos> expired = new ArrayList<>();
        STATE.forEach((pos, entry) -> {
            if (entry.tickets.isEmpty()) {
                return;
            }
            entry.tickets.entrySet().removeIf(ticket -> {
                boolean isExpired = ticket.getValue().isExpired(gameTime);
                if (isExpired && ModConstants.LOGGER.isTraceEnabled()) {
                    ModConstants.LOGGER.trace("[ChunkStream][{}] Ticket {} expired at tick {}.", pos, ticket.getKey(), gameTime);
                }
                return isExpired;
            });
            if (entry.tickets.isEmpty()) {
                entry.transitionTo(pos, ChunkState.UNLOADING);
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
            entry.transitionTo(pos, ChunkState.READY);
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

    public static boolean isWarmCached(ChunkPos pos) {
        synchronized (WARM_CACHE) {
            return WARM_CACHE.containsKey(pos);
        }
    }

    private static final class ChunkStatusEntry {
        private volatile ChunkState state = ChunkState.UNLOADED;
        private final Map<ChunkTicketType, ChunkTicket> tickets = new EnumMap<>(ChunkTicketType.class);
        private volatile long lastTouched;
        private volatile CompoundTag lastPersisted;
        private volatile DirtySegmentSet dirtySegments = DirtySegmentSet.none();

        void upsertTicket(ChunkPos pos, ChunkTicketType type, long expiryTick) {
            tickets.put(type, new ChunkTicket(type, expiryTick));
            if (ModConstants.LOGGER.isTraceEnabled()) {
                ModConstants.LOGGER.trace("[ChunkStream][{}] Upserted ticket {} expiring at tick {}.", pos, type, expiryTick);
            }
        }

        void transitionTo(ChunkPos pos, ChunkState newState) {
            if (state == newState) {
                return;
            }
            boolean allowDemotion = state == ChunkState.ACTIVE && newState == ChunkState.READY;
            if (newState == ChunkState.UNLOADED || newState.ordinal() >= state.ordinal() || allowDemotion) {
                ChunkState previous = state;
                state = newState;
                if (ModConstants.LOGGER.isTraceEnabled()) {
                    ModConstants.LOGGER.trace("[ChunkStream][{}] State {} -> {}.", pos, previous, newState);
                }
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
        private final Map<ChunkPos, CompletableFuture<Optional<CompoundTag>>> loadTasks = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<LoadRequest> pendingLoadQueue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger pendingLoadCount = new AtomicInteger();
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
            LoadRequest request = new LoadRequest(
                    pos,
                    requestedDimension != null ? requestedDimension : dimension,
                    new CompletableFuture<>(),
                    System.nanoTime()
            );
            if (dispatchLoad(values, request)) {
                return request.future().exceptionally(ex -> {
                    ModConstants.LOGGER.error("[ChunkStream] Failed to read chunk {}", pos, ex);
                    return Optional.empty();
                });
            }

            int queueLimit = loadQueueLimit(values);
            int queued = pendingLoadCount.incrementAndGet();
            if (queued > queueLimit) {
                pendingLoadCount.decrementAndGet();
                ModConstants.LOGGER.warn("[ChunkStream] Load queue full ({}); dropping request for {}", queueLimit, pos);
                request.future().complete(Optional.empty());
                return request.future();
            }
            pendingLoadQueue.offer(request);
            return request.future().exceptionally(ex -> {
                ModConstants.LOGGER.error("[ChunkStream] Failed to read chunk {}", pos, ex);
                return Optional.empty();
            });
        }

        void enqueueSave(ChunkPos pos, CompoundTag payload, DirtySegmentSet dirtySegments, long gameTime, ResourceKey<Level> requestedDimension) {
            pendingSaves.merge(
                    pos,
                    new PendingSave(payload, dirtySegments, gameTime + configSupplier.get().saveDebounceTicks(),
                            requestedDimension != null ? requestedDimension : dimension),
                    (existing, incoming) -> new PendingSave(
                            incoming.payload(),
                            existing.dirtySegments().mergedWith(incoming.dirtySegments()),
                            incoming.scheduledTick(),
                            incoming.dimension())
            );
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
                        return submitWrite(entry.getKey(), entry.getValue());
                    })
                    .toArray(CompletableFuture[]::new));
        }

        void flushChunk(ChunkPos pos) {
            PendingSave save = pendingSaves.remove(pos);
            if (save != null) {
                writeImmediately(pos, save.payload(), save.dimension());
            }
        }

        void flushAll() {
            pendingSaves.forEach((pos, save) -> writeImmediately(pos, save.payload(), save.dimension()));
            pendingSaves.clear();
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
            submitWrite(pos, pending);
            return true;
        }

        private CompletableFuture<Void> submitWrite(ChunkPos pos, PendingSave save) {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            IoExecutors.submit(save.dimension(), "chunk-save-" + pos, () -> {
                try {
                    ChunkStorageAdapter adapter = adapterSupplier.get();
                    if (adapter == null) {
                        completion.complete(null);
                        return;
                    }

                    DirtySegmentSet dirty = save.dirtySegments();
                    CompoundTag payload = save.payload();
                    if (dirty.hasEntries()) {
                        if (dirty.isFullChunkDirty()) {
                            payload = save.payload().copy();
                        } else {
                            ChunkStatusEntry statusEntry = STATE.get(pos);
                            CompoundTag baseline = statusEntry != null ? statusEntry.getLastPersisted() : null;
                            payload = mergeDirtySegments(baseline, payload, dirty);
                            if (statusEntry != null) {
                                statusEntry.setLastPersisted(payload.copy());
                            }
                        }
                    }

                    adapter.write(pos, payload);
                    ChunkStatusEntry statusEntry = STATE.get(pos);
                    if (statusEntry != null) {
                        statusEntry.setLastPersisted(payload.copy());
                    }
                } catch (Exception e) {
                    ModConstants.LOGGER.error("[ChunkStream] Failed to write chunk {}", pos, e);
                    completion.completeExceptionally(e);
                }
            }).whenComplete((ignored, throwable) -> {
                if (throwable != null && !completion.isDone()) {
                    completion.completeExceptionally(throwable);
                } else if (!completion.isDone()) {
                    completion.complete(null);
                }
                inFlight.decrementAndGet();
                drainQueuedLoads();
            });
            return completion;
        }

        private boolean dispatchLoad(ChunkStreamingConfig.ChunkConfigValues values, LoadRequest request) {
            if (inFlight.incrementAndGet() > values.maxParallelIo()) {
                inFlight.decrementAndGet();
                return false;
            }
            startLoad(request, values);
            return true;
        }

        private void startLoad(LoadRequest request, ChunkStreamingConfig.ChunkConfigValues values) {
            CompletableFuture<Optional<CompoundTag>> payloadFuture = request.future();
            loadTasks.put(request.pos(), payloadFuture);
            IoExecutors.submit(request.dimension(), "chunk-load-" + request.pos(), () -> {
                try {
                    ChunkStorageAdapter adapter = adapterSupplier.get();
                    if (adapter == null) {
                        payloadFuture.complete(Optional.empty());
                        return;
                    }
                    Optional<CompoundTag> payload = adapter.read(request.pos());
                    payloadFuture.complete(payload);
                } catch (Exception e) {
                    payloadFuture.completeExceptionally(e);
                }
            }).whenComplete((ignored, throwable) -> {
                if (throwable != null && !payloadFuture.isDone()) {
                    payloadFuture.completeExceptionally(throwable);
                }
                loadTasks.remove(request.pos());
                inFlight.decrementAndGet();
                long queuedDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - request.enqueuedAt());
                if (ModConstants.LOGGER.isDebugEnabled() && queuedDurationMs > 0) {
                    ModConstants.LOGGER.debug("[ChunkStream] Load {} waited {} ms before execution (queue size: {}).",
                            request.pos(), queuedDurationMs, pendingLoadCount.get());
                }
                drainQueuedLoads(values);
            });
        }

        private void drainQueuedLoads() {
            drainQueuedLoads(configSupplier.get());
        }

        private void drainQueuedLoads(ChunkStreamingConfig.ChunkConfigValues values) {
            while (true) {
                if (inFlight.get() >= values.maxParallelIo()) {
                    return;
                }
                LoadRequest next = pendingLoadQueue.poll();
                if (next == null) {
                    return;
                }
                pendingLoadCount.decrementAndGet();
                if (!dispatchLoad(values, next)) {
                    pendingLoadQueue.offer(next);
                    pendingLoadCount.incrementAndGet();
                    return;
                }
            }
        }

        private int loadQueueLimit(ChunkStreamingConfig.ChunkConfigValues values) {
            return Math.max(values.maxParallelIo() * 4, Math.min(256, values.ioQueueSize()));
        }

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
                return dirty.sectionYLevels().contains(y);
            });

            for (Integer y : dirty.sectionYLevels()) {
                CompoundTag updatedSection = latestSections.get(y);
                if (updatedSection != null) {
                    sections.add(updatedSection.copy());
                }
            }

            merged.put("sections", sections);
            return merged;
        }

        private void writeImmediately(ChunkPos pos, CompoundTag payload, ResourceKey<Level> requestedDimension) {
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

    private record PendingSave(CompoundTag payload, DirtySegmentSet dirtySegments, long scheduledTick, ResourceKey<Level> dimension) {
    }
    private record LoadRequest(ChunkPos pos, ResourceKey<Level> dimension, CompletableFuture<Optional<CompoundTag>> future, long enqueuedAt) {
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
