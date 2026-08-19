package com.thunder.wildernessodysseyapi.dataengine;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.async.AsyncDataTask;
import com.thunder.wildernessodysseyapi.dataengine.async.CompletedTaskQueue;
import com.thunder.wildernessodysseyapi.dataengine.async.DataWorkerPool;
import com.thunder.wildernessodysseyapi.dataengine.cache.DataCache;
import com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.dataengine.debug.DataEngineDebugIntegration;
import com.thunder.wildernessodysseyapi.dataengine.dirty.DirtyEntry;
import com.thunder.wildernessodysseyapi.dataengine.dirty.DirtyTracker;
import com.thunder.wildernessodysseyapi.dataengine.interest.InterestManager;
import com.thunder.wildernessodysseyapi.dataengine.interest.InterestProfile;
import com.thunder.wildernessodysseyapi.dataengine.interest.InterestRegion;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetrics;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetricsSnapshot;
import com.thunder.wildernessodysseyapi.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.dataengine.network.DataSyncManager;
import com.thunder.wildernessodysseyapi.dataengine.queue.DataUpdateQueue;
import com.thunder.wildernessodysseyapi.dataengine.queue.QueuedUpdate;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.dataengine.scheduler.DataUpdateScheduler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Central server-owned high-performance data layer for Wilderness Odyssey.
 *
 * <p>This service does not replace Minecraft's tick engine. NeoForge invokes
 * {@link #tick(MinecraftServer)} once per eligible server tick; the service then
 * schedules only Wilderness-owned work, drains pushed dirty state, applies
 * completed immutable-snapshot calculations, and flushes bounded delta batches.</p>
 *
 * <p>Unless a method explicitly says otherwise, mutation methods are SERVER
 * THREAD ONLY. Worker threads may call only task {@code compute()} methods and
 * thread-safe metric/completion primitives.</p>
 */
public final class DataEngine {
    private static final DataEngine INSTANCE = new DataEngine();
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final Map<ResourceLocation, DataSystemRegistration> systems = new HashMap<>();
    private final Map<CacheId, DataCache<?, ?>> caches = new HashMap<>();
    private final DataEngineMetrics metrics = new DataEngineMetrics(DataEngineConfig.defaults().metrics());

    private DataEngineConfig.Values config = DataEngineConfig.defaults();
    private DirtyTracker dirtyTracker = new DirtyTracker(config.maxDirtyEntries());
    private DataUpdateQueue updateQueue = new DataUpdateQueue(config.maxQueueSize());
    private CompletedTaskQueue completedTasks = new CompletedTaskQueue(config.maxCompletedTasks());
    private DataUpdateScheduler scheduler = new DataUpdateScheduler();
    private InterestManager interestManager = new InterestManager();
    private DataSyncManager syncManager = new DataSyncManager(interestManager, metrics, config);
    private DataWorkerPool workerPool = new DataWorkerPool(completedTasks, metrics, config.maxAsyncInFlight());

    private MinecraftServer server;
    private long lastBackpressureWarningNanos;

    private DataEngine() {
    }

    /** Returns the process singleton; live state is reset for each server lifecycle. */
    public static DataEngine get() {
        return INSTANCE;
    }

    /** SERVER THREAD ONLY. Creates fresh bounded state for a starting server. */
    public void start(MinecraftServer server) {
        Objects.requireNonNull(server, "Minecraft server is required");
        if (this.server != null) {
            shutdown();
        }
        this.server = server;
        config = DataEngineConfig.values();
        dirtyTracker = new DirtyTracker(config.maxDirtyEntries());
        updateQueue = new DataUpdateQueue(config.maxQueueSize());
        completedTasks = new CompletedTaskQueue(config.maxCompletedTasks());
        scheduler = new DataUpdateScheduler();
        interestManager = new InterestManager();
        syncManager = new DataSyncManager(interestManager, metrics, config);
        workerPool = new DataWorkerPool(completedTasks, metrics, config.maxAsyncInFlight());
        systems.clear();
        caches.clear();
        metrics.beginServerLifecycle(config.metrics());
        lastBackpressureWarningNanos = 0L;

        DataEngineDebugIntegration.register(this);
        ModConstants.LOGGER.info(
                "[Data Engine] Initialized (enabled: {}, non-critical budget: {} ms, queue bound: {})",
                config.enabled(),
                String.format(java.util.Locale.ROOT, "%.2f", config.tickBudgetNanos() / 1_000_000.0D),
                config.maxQueueSize()
        );
    }

    /**
     * SERVER THREAD ONLY. Advances the named pipeline while Minecraft has spare time.
     *
     * <p>The allowance is live rather than a snapshot. Every stage and queue
     * callback yields once Minecraft reserves the rest of the tick for its own
     * work, including chunk IO and generation completion.</p>
     */
    public void tick(MinecraftServer server, BooleanSupplier serverHasTime) {
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        if (this.server != server || !config.enabled() || !serverHasTime.getAsBoolean()) {
            return;
        }
        long startedNanos = System.nanoTime();
        long deadlineNanos = saturatingAdd(startedNanos, config.tickBudgetNanos());
        long currentTick = server.getTickCount();

        refreshPlayerInterest(server, currentTick);
        if (!serverHasTime.getAsBoolean()) {
            updateMetrics(startedNanos);
            return;
        }
        scheduleDueSystems(server, currentTick);
        processCriticalDirty(server, currentTick, serverHasTime);
        processCriticalQueue(serverHasTime);
        processCriticalCompletedTasks(serverHasTime);
        processBudgetedWork(server, currentTick, deadlineNanos, serverHasTime);
        flushNetworkBatches(server, currentTick, deadlineNanos, serverHasTime);
        updateMetrics(startedNanos);
    }

    /** SERVER THREAD ONLY. Releases every world/server-derived reference. */
    public void shutdown() {
        if (server == null) {
            return;
        }
        workerPool.shutdown();
        updateQueue.clear();
        dirtyTracker.clear();
        completedTasks.clear();
        scheduler.clear();
        interestManager.clear();
        syncManager.clear();
        for (DataCache<?, ?> cache : caches.values()) {
            cache.clear();
        }
        caches.clear();
        systems.clear();
        server = null;
        ModConstants.LOGGER.info("[Data Engine] Shut down and released server state");
    }

    /**
     * Applies reload-safe settings. Structural queue bounds take effect on the
     * next server start so live queued work is never silently discarded.
     */
    public void reload(DataEngineConfig.Values values) {
        Objects.requireNonNull(values, "Data Engine config values are required");
        boolean structuralChange = values.maxQueueSize() != config.maxQueueSize()
                || values.maxDirtyEntries() != config.maxDirtyEntries()
                || values.maxCompletedTasks() != config.maxCompletedTasks()
                || values.maxAsyncInFlight() != config.maxAsyncInFlight();
        config = values;
        metrics.setEnabled(values.metrics());
        syncManager.updateConfig(values);
        if (server != null && structuralChange) {
            ModConstants.LOGGER.info("[Data Engine] Queue/async bounds changed; new bounds apply after server restart");
        }
    }

    /** SERVER THREAD ONLY. Registers one gameplay/debug subsystem exactly once. */
    public void registerSystem(DataSystemRegistration registration) {
        ensureRunning();
        Objects.requireNonNull(registration, "System registration is required");
        if (systems.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Data Engine system is already registered: " + registration.id());
        }
        metrics.registerSystem(registration.id());
        scheduler.register(registration, server.getTickCount());
    }

    /** Marks final state dirty using the current server tick. */
    public boolean markDirty(
            ResourceLocation systemId,
            long objectKey,
            String reason,
            UpdatePriority priority
    ) {
        ensureRunning();
        DataSystemRegistration registration = systems.get(systemId);
        if (registration == null || registration.dirtyHandler() == null) {
            throw new IllegalArgumentException("System has no registered dirty handler: " + systemId);
        }
        DirtyTracker.MarkResult result = dirtyTracker.markDirty(
                systemId,
                objectKey,
                priority,
                reason,
                server.getTickCount()
        );
        if (result == DirtyTracker.MarkResult.COALESCED) {
            metrics.recordNetworkCoalesced();
        } else if (result.activatedBackpressure()) {
            metrics.recordDroppedOrSuperseded();
            warnBackpressure("dirty tracker", systemId);
        }
        return result.accepted();
    }

    /** SERVER THREAD ONLY. Submits an explicit coalescible or individual action. */
    public boolean submit(QueuedUpdate update) {
        ensureRunning();
        DataUpdateQueue.SubmissionResult result = updateQueue.submit(update);
        metrics.recordSubmission(update.systemId(), result);
        if (result.activatedBackpressure()) {
            warnBackpressure("update queue", update.systemId());
        }
        return result.accepted();
    }

    /**
     * SERVER THREAD ONLY. Submits pure immutable-snapshot calculation to shared workers.
     */
    public <R> boolean runAsync(
            ResourceLocation systemId,
            String label,
            UpdatePriority priority,
            boolean supersedable,
            AsyncDataTask<R> task
    ) {
        ensureRegistered(systemId);
        return workerPool.submit(systemId, label, priority, supersedable, task);
    }

    /** SERVER THREAD ONLY. Sends one compact delta to spatially interested players. */
    public int sendDelta(
            InterestRegion region,
            InterestProfile profile,
            DataDelta delta
    ) {
        ensureRunning();
        return syncManager.sendToRegion(server, region, profile, delta, server.getTickCount());
    }

    /** SERVER THREAD ONLY. Sends one compact delta to explicit feature subscribers. */
    public int sendDeltaToFeature(ResourceLocation featureId, DataDelta delta) {
        ensureRunning();
        return syncManager.sendToFeature(featureId, delta, server.getTickCount());
    }

    /** SERVER THREAD ONLY. Sends or batches one delta for a known player. */
    public boolean sendDelta(ServerPlayer player, DataDelta delta) {
        ensureRunning();
        return syncManager.sendToPlayer(player, delta, server.getTickCount());
    }

    /** SERVER THREAD ONLY. Updates an explicit player subscription. */
    public void setFeatureInterest(ServerPlayer player, ResourceLocation featureId, boolean interested) {
        ensureRunning();
        interestManager.setFeatureInterest(player, featureId, interested, server.getTickCount());
    }

    /**
     * Registers a bounded typed cache owned by one system. Duplicate names are
     * rejected to prevent unsafe generic casts between unrelated owners.
     */
    public <K, V> DataCache<K, V> registerCache(ResourceLocation systemId, String name, int maximumEntries) {
        ensureRegistered(systemId);
        CacheId id = new CacheId(systemId, Objects.requireNonNull(name, "Cache name is required"));
        DataCache<K, V> cache = new DataCache<>(
                maximumEntries,
                metrics::recordCacheHit,
                metrics::recordCacheMiss,
                ignored -> { }
        );
        if (caches.putIfAbsent(id, cache) != null) {
            throw new IllegalArgumentException("Data Engine cache is already registered: " + id);
        }
        return cache;
    }

    /** Registers a cache using the server-configured default LRU bound. */
    public <K, V> DataCache<K, V> registerCache(ResourceLocation systemId, String name) {
        return registerCache(systemId, name, config.defaultCacheMaxEntries());
    }

    public InterestManager interest() {
        return interestManager;
    }

    public DataEngineMetricsSnapshot metricsSnapshot() {
        return metrics.snapshot();
    }

    /** SERVER THREAD ONLY. Resets diagnostic totals without discarding pending work. */
    public void resetMetrics() {
        ensureRunning();
        metrics.resetCounters();
    }

    public DataEngineConfig.Values config() {
        return config;
    }

    public boolean isRunning() {
        return server != null;
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    private void refreshPlayerInterest(MinecraftServer server, long currentTick) {
        interestManager.refresh(server, currentTick);
    }

    private void scheduleDueSystems(MinecraftServer server, long currentTick) {
        scheduler.collectDue(currentTick, registration -> submit(QueuedUpdate.scheduled(
                registration.id(),
                registration.priority(),
                currentTick,
                () -> runScheduledHandler(server, registration)
        )));
    }

    private void processCriticalDirty(
            MinecraftServer server,
            long currentTick,
            BooleanSupplier serverHasTime
    ) {
        DirtyEntry entry;
        while (serverHasTime.getAsBoolean() && (entry = dirtyTracker.pollCritical()) != null) {
            if (!enqueueDirtyHandler(server, currentTick, entry)) {
                dirtyTracker.requeue(entry);
                break;
            }
        }
    }

    private void processCriticalQueue(BooleanSupplier serverHasTime) {
        QueuedUpdate update;
        while (serverHasTime.getAsBoolean() && (update = updateQueue.pollCritical()) != null) {
            runUpdate(update);
        }
    }

    private void processCriticalCompletedTasks(BooleanSupplier serverHasTime) {
        CompletedTaskQueue.CompletedTask task;
        while (serverHasTime.getAsBoolean() && (task = completedTasks.pollCritical()) != null) {
            runCompletedTask(task);
        }
    }

    private void processBudgetedWork(
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        while (serverHasTime.getAsBoolean() && System.nanoTime() < deadlineNanos) {
            boolean progressed = false;

            CompletedTaskQueue.CompletedTask completed = completedTasks.pollNonCritical();
            if (completed != null) {
                runCompletedTask(completed);
                progressed = true;
            }

            if (serverHasTime.getAsBoolean() && System.nanoTime() < deadlineNanos) {
                DirtyEntry dirty = dirtyTracker.pollNonCritical();
                if (dirty != null) {
                    if (!enqueueDirtyHandler(server, currentTick, dirty)) {
                        dirtyTracker.requeue(dirty);
                        return;
                    }
                    progressed = true;
                }
            }

            if (serverHasTime.getAsBoolean() && System.nanoTime() < deadlineNanos) {
                QueuedUpdate update = updateQueue.pollNonCritical();
                if (update != null) {
                    runUpdate(update);
                    progressed = true;
                }
            }

            if (!progressed) {
                return;
            }
        }
    }

    private void flushNetworkBatches(
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        if (serverHasTime.getAsBoolean() && System.nanoTime() < deadlineNanos) {
            syncManager.flush(server, currentTick, deadlineNanos, serverHasTime);
        }
    }

    private boolean enqueueDirtyHandler(MinecraftServer server, long currentTick, DirtyEntry entry) {
        DataSystemRegistration registration = systems.get(entry.systemId());
        if (registration == null || registration.dirtyHandler() == null) {
            metrics.recordFailure(entry.systemId());
            ModConstants.LOGGER.error("[Data Engine] Dropping dirty entry for unregistered handler {}", entry.systemId());
            return true;
        }
        return submit(QueuedUpdate.dirty(
                entry.systemId(),
                entry.objectKey(),
                entry.priority(),
                currentTick,
                () -> runDirtyHandler(server, registration, entry)
        ));
    }

    private void runScheduledHandler(MinecraftServer server, DataSystemRegistration registration) {
        try {
            registration.scheduledHandler().run(server);
        } catch (Exception exception) {
            throw new DataEngineTaskException(registration.id(), "scheduled update", exception);
        }
    }

    private void runDirtyHandler(
            MinecraftServer server,
            DataSystemRegistration registration,
            DirtyEntry entry
    ) {
        try {
            registration.dirtyHandler().run(server, entry);
        } catch (Exception exception) {
            throw new DataEngineTaskException(registration.id(), "dirty update", exception);
        }
    }

    private void runUpdate(QueuedUpdate update) {
        long startedNanos = System.nanoTime();
        try {
            update.run();
            metrics.recordProcessed(update.systemId(), System.nanoTime() - startedNanos);
        } catch (RuntimeException exception) {
            metrics.recordFailure(update.systemId());
            ModConstants.LOGGER.error(
                    "[Data Engine] Isolated failed queued task for {} and continued processing",
                    update.systemId(),
                    exception
            );
        }
    }

    private void runCompletedTask(CompletedTaskQueue.CompletedTask task) {
        long startedNanos = System.nanoTime();
        try {
            task.run();
            metrics.recordProcessed(task.systemId(), System.nanoTime() - startedNanos);
        } catch (RuntimeException exception) {
            metrics.recordFailure(task.systemId());
            ModConstants.LOGGER.error(
                    "[Data Engine] Isolated failed async apply task for {} and continued processing",
                    task.systemId(),
                    exception
            );
        }
    }

    private void updateMetrics(long startedNanos) {
        int cacheEntries = 0;
        for (DataCache<?, ?> cache : caches.values()) {
            cacheEntries += cache.size();
        }
        int queued = updateQueue.size() + completedTasks.size() + syncManager.pendingEntries();
        boolean backpressure = queued >= Math.max(1, config.maxQueueSize() * 3 / 4)
                || dirtyTracker.size() >= Math.max(1, config.maxDirtyEntries() * 3 / 4)
                || workerPool.inFlight() >= Math.max(1, workerPool.maximumInFlight() * 3 / 4);
        metrics.updateGauges(
                dirtyTracker.size(),
                queued,
                updateQueue.peakSize(),
                cacheEntries,
                AsyncTaskManager.queuedCpuWorkTasks() + completedTasks.size(),
                backpressure
        );
        long nowNanos = System.nanoTime();
        metrics.recordMainThreadProcessing(nowNanos - startedNanos);
        metrics.sampleRates(nowNanos);
    }

    private void ensureRunning() {
        if (server == null) {
            throw new IllegalStateException("Data Engine is not attached to a running server");
        }
    }

    private void ensureRegistered(ResourceLocation systemId) {
        ensureRunning();
        if (!systems.containsKey(systemId)) {
            throw new IllegalArgumentException("Data Engine system is not registered: " + systemId);
        }
    }

    private void warnBackpressure(String stage, ResourceLocation systemId) {
        long now = System.nanoTime();
        if (!config.debugLogging() || now - lastBackpressureWarningNanos < WARNING_INTERVAL_NANOS) {
            return;
        }
        lastBackpressureWarningNanos = now;
        ModConstants.LOGGER.warn(
                "[Data Engine] Backpressure active in {} for {} (dirty {}, queued {}, completed {}, network {})",
                stage,
                systemId,
                dirtyTracker.size(),
                updateQueue.size(),
                completedTasks.size(),
                syncManager.pendingEntries()
        );
    }

    private static long saturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private record CacheId(ResourceLocation systemId, String name) {
    }

    private static final class DataEngineTaskException extends RuntimeException {
        private DataEngineTaskException(ResourceLocation systemId, String stage, Throwable cause) {
            super("Data Engine " + stage + " failed for " + systemId, cause);
        }
    }
}
