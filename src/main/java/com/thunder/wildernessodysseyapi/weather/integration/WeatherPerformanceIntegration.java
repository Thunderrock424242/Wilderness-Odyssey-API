package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import com.thunder.wildernessodysseyapi.dataengine.async.AsyncDataTask;
import com.thunder.wildernessodysseyapi.dataengine.queue.QueuedUpdate;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.dataengine.scheduler.UpdateFrequency;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.AdaptiveThrottle;
import com.thunder.wildernessodysseyapi.performance.tickengine.SubsystemPolicy;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Adapts optional weather maintenance to the Data and Tick engines.
 *
 * <p>{@link WeatherAuthority} remains the only simulation and persistence
 * owner. The adapter contributes one central cadence, one bounded callback per
 * loaded level, coalesced final-state publication, and real Tick Engine timing.
 * It never calculates weather independently or takes chunk-lifecycle control.</p>
 */
public final class WeatherPerformanceIntegration {
    public static final ResourceLocation SYSTEM_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "weather_runtime"
    );

    private static final Map<ResourceKey<Level>, LevelHandle> LEVEL_HANDLES = new HashMap<>();
    private static final Map<Long, LevelHandle> HANDLES_BY_ID = new HashMap<>();
    private static final long ASYNC_TIMEOUT_TICKS = 200L;

    private static MinecraftServer registeredServer;
    private static long nextLevelId = 1L;

    private WeatherPerformanceIntegration() {
    }

    /** Registers weather maintenance for one server lifecycle. */
    public static void register(DataEngine engine, MinecraftServer server) {
        Objects.requireNonNull(engine, "Data Engine is required");
        registeredServer = Objects.requireNonNull(server, "Minecraft server is required");
        LEVEL_HANDLES.clear();
        HANDLES_BY_ID.clear();
        nextLevelId = 1L;

        engine.registerSystem(DataSystemRegistration.builder(SYSTEM_ID)
                .frequency(UpdateFrequency.FAST)
                .intervalTicks(WeatherPerformanceIntegration::currentPollIntervalTicks)
                .priority(UpdatePriority.NORMAL)
                .onScheduledUpdate(WeatherPerformanceIntegration::enqueueLevelMaintenance)
                .onDirtyUpdate((currentServer, dirty) -> publishDirtySnapshot(
                        currentServer,
                        dirty.objectKey()
                ))
                .build());
    }

    /**
     * Preserves authoritative weather when an operator disables the Data Engine.
     *
     * <p>Each dimension and publication rechecks the live allowance. No missed
     * interval is replayed as a burst when spare time returns.</p>
     */
    public static void runFallbackIfDataEngineDisabled(
            MinecraftServer server,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(server, "Minecraft server is required");
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        DataEngine engine = DataEngine.get();
        if (engine.isRunning() && engine.isEnabled()) {
            return;
        }

        WeatherAuthority authority = WeatherAuthority.get();
        long currentTick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            if (!serverHasTime.getAsBoolean()) {
                return;
            }
            LevelHandle handle = handle(level);
            abandonInFlightCalculation(handle, authority);
            long startedNanos = System.nanoTime();
            WeatherAuthority.OptionalMaintenanceResult result;
            try {
                result = authority.runOptionalMaintenance(level);
            } finally {
                TickEngine.metrics().recordExecution(
                        "weather",
                        System.nanoTime() - startedNanos,
                        currentTick
                );
            }
            if (result.snapshotDue()) {
                handle.snapshotPending = true;
            }
            if (handle.snapshotPending && serverHasTime.getAsBoolean()) {
                TickEngine.metrics().time(
                        "weather",
                        () -> authority.publishSnapshot(level),
                        currentTick
                );
                handle.snapshotPending = false;
            }
        }
    }

    /** Removes a level from the session-local callback and dirty-key index. */
    public static void forgetLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        LevelHandle handle = LEVEL_HANDLES.get(level.dimension());
        if (handle == null || handle.level != level) {
            return;
        }
        LEVEL_HANDLES.remove(level.dimension());
        HANDLES_BY_ID.remove(handle.id);
    }

    /** Clears process references after queued Data Engine work has stopped. */
    public static void shutdown() {
        registeredServer = null;
        LEVEL_HANDLES.clear();
        HANDLES_BY_ID.clear();
        nextLevelId = 1L;
    }

    static int pollInterval(
            AdaptiveThrottle throttle,
            TickPressure pressure,
            ActivityLevel activity,
            double recoveryMultiplier,
            int configuredIntervalTicks
    ) {
        int configured = Math.max(1, configuredIntervalTicks);
        SubsystemPolicy policy = throttle.policy("weather");
        int governedBase = policy == null
                ? configured
                : Math.min(configured, policy.maximumIntervalTicks());
        int governed = throttle.intervalFor(
                "weather",
                governedBase,
                pressure,
                activity,
                recoveryMultiplier
        );
        // A performance governor may slow work, but it must not silently make
        // an operator's deliberately slower weather interval run more often.
        return Math.max(configured, governed);
    }

    static long snapshotObjectKey(long levelId) {
        if (levelId <= 0L) {
            throw new IllegalArgumentException("Weather level id must be positive");
        }
        return -levelId;
    }

    private static int currentPollIntervalTicks() {
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        int configured = Math.min(
                scheduling.simulationIntervalTicks(),
                scheduling.snapshotSyncIntervalTicks()
        );
        MinecraftServer server = registeredServer;
        ActivityLevel activity = server != null && hasRelevantPlayer(server)
                ? ActivityLevel.ACTIVE
                : ActivityLevel.DORMANT;
        return pollInterval(
                TickEngine.throttle(),
                TickEngine.pressure(),
                activity,
                TickEngine.recoveryMultiplier(),
                configured
        );
    }

    private static void enqueueLevelMaintenance(MinecraftServer server) {
        DataEngine engine = DataEngine.get();
        long currentTick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            LevelHandle handle = handle(level);
            engine.submit(QueuedUpdate.dirty(
                    SYSTEM_ID,
                    handle.id,
                    UpdatePriority.NORMAL,
                    currentTick,
                    () -> runLevelMaintenance(server, handle)
            ));
        }
    }

    private static void runLevelMaintenance(MinecraftServer server, LevelHandle handle) {
        if (!isCurrent(server, handle)) {
            return;
        }

        long currentTick = server.getTickCount();
        WeatherAuthority authority = WeatherAuthority.get();
        expireTimedOutCalculation(server, handle, authority, currentTick);
        long startedNanos = System.nanoTime();
        WeatherAuthority.OptionalMaintenancePreparation preparation;
        try {
            preparation = authority.prepareOptionalMaintenance(
                    handle.level,
                    !handle.calculationInFlight
            );
        } finally {
            TickEngine.metrics().recordExecution(
                    "weather",
                    System.nanoTime() - startedNanos,
                    currentTick
                );
        }

        WeatherAuthority.SimulationBatch batch = preparation.simulationBatch();
        if (batch == null) {
            if (preparation.snapshotDue()) {
                if (handle.calculationInFlight) {
                    handle.publishAfterCalculation = true;
                } else {
                    requestSnapshot(handle, "authoritative weather snapshot due");
                }
            }
            return;
        }

        if (batch.cellCount() == 0) {
            authority.markSimulationBatchScheduled(handle.level, batch);
            WeatherAuthority.SimulationResult result = authority.calculateSimulationBatch(batch);
            long applyStartedNanos = System.nanoTime();
            try {
                if (!authority.applySimulationResult(handle.level, result)) {
                    authority.requestSimulationRetry(handle.level, batch);
                }
            } finally {
                TickEngine.metrics().recordExecution(
                        "weather",
                        System.nanoTime() - applyStartedNanos,
                        currentTick
                );
            }
            if (preparation.snapshotDue()) {
                requestSnapshot(handle, "authoritative weather snapshot due");
            }
            return;
        }

        submitSimulation(server, handle, authority, batch, preparation.snapshotDue(), currentTick);
    }

    private static void submitSimulation(
            MinecraftServer server,
            LevelHandle handle,
            WeatherAuthority authority,
            WeatherAuthority.SimulationBatch batch,
            boolean snapshotDue,
            long currentTick
    ) {
        long submissionId = nextSubmissionId(handle);
        handle.calculationInFlight = true;
        handle.calculationStartedTick = currentTick;
        handle.activeBatch = batch;
        handle.publishAfterCalculation = snapshotDue;

        boolean accepted = DataEngine.get().runAsync(
                SYSTEM_ID,
                "weather_" + handle.id,
                UpdatePriority.NORMAL,
                true,
                new AsyncDataTask<WeatherAuthority.SimulationResult>() {
                    @Override
                    public WeatherAuthority.SimulationResult compute() {
                        return authority.calculateSimulationBatch(batch);
                    }

                    @Override
                    public boolean isStillValid(WeatherAuthority.SimulationResult result) {
                        return isMatchingCalculation(server, handle, batch, submissionId)
                                && authority.isSimulationResultCurrent(handle.level, result);
                    }

                    @Override
                    public void apply(WeatherAuthority.SimulationResult result) {
                        applyCompletedSimulation(
                                server,
                                handle,
                                authority,
                                batch,
                                submissionId,
                                result
                        );
                    }

                    @Override
                    public void onDiscarded(WeatherAuthority.SimulationResult result) {
                        discardSimulation(server, handle, authority, batch, submissionId);
                    }
                }
        );
        if (accepted) {
            authority.markSimulationBatchScheduled(handle.level, batch);
            return;
        }

        clearCalculation(handle, batch, submissionId);
        // Explicit backpressure never moves atmospheric math onto the caller.
        // The cadence watermark was not advanced, so the next poll retries.
        if (snapshotDue) {
            requestSnapshot(handle, "weather worker saturated; publish current state");
        }
    }

    private static void applyCompletedSimulation(
            MinecraftServer server,
            LevelHandle handle,
            WeatherAuthority authority,
            WeatherAuthority.SimulationBatch batch,
            long submissionId,
            WeatherAuthority.SimulationResult result
    ) {
        long startedNanos = System.nanoTime();
        boolean applied = false;
        try {
            applied = authority.applySimulationResult(handle.level, result);
            if (!applied) {
                authority.requestSimulationRetry(handle.level, batch);
            }
        } catch (RuntimeException exception) {
            authority.requestSimulationRetry(handle.level, batch);
            throw exception;
        } finally {
            TickEngine.metrics().recordExecution(
                    "weather",
                    System.nanoTime() - startedNanos,
                    server.getTickCount()
            );
            if (isMatchingCalculation(server, handle, batch, submissionId)) {
                boolean publishAfter = handle.publishAfterCalculation;
                clearCalculation(handle, batch, submissionId);
                if (publishAfter) {
                    requestSnapshot(
                            handle,
                            applied
                                    ? "weather calculation applied"
                                    : "weather calculation rejected during apply; publish current state"
                    );
                }
            }
        }
    }

    private static void discardSimulation(
            MinecraftServer server,
            LevelHandle handle,
            WeatherAuthority authority,
            WeatherAuthority.SimulationBatch batch,
            long submissionId
    ) {
        if (!isMatchingCalculation(server, handle, batch, submissionId)) {
            return;
        }
        boolean publishAfter = handle.publishAfterCalculation;
        clearCalculation(handle, batch, submissionId);
        authority.requestSimulationRetry(handle.level, batch);
        if (publishAfter) {
            requestSnapshot(handle, "weather calculation discarded; publish current state");
        }
    }

    private static void expireTimedOutCalculation(
            MinecraftServer server,
            LevelHandle handle,
            WeatherAuthority authority,
            long currentTick
    ) {
        if (!handle.calculationInFlight
                || !calculationTimedOut(currentTick, handle.calculationStartedTick)) {
            return;
        }
        WeatherAuthority.SimulationBatch batch = handle.activeBatch;
        boolean publishAfter = handle.publishAfterCalculation;
        handle.calculationInFlight = false;
        handle.calculationStartedTick = Long.MIN_VALUE;
        handle.activeBatch = null;
        handle.publishAfterCalculation = false;
        authority.requestSimulationRetry(handle.level, batch);
        if (publishAfter && isCurrent(server, handle)) {
            requestSnapshot(handle, "weather calculation timed out; publish current state");
        }
    }

    private static void abandonInFlightCalculation(
            LevelHandle handle,
            WeatherAuthority authority
    ) {
        if (!handle.calculationInFlight) {
            return;
        }
        WeatherAuthority.SimulationBatch batch = handle.activeBatch;
        handle.snapshotPending |= handle.publishAfterCalculation;
        handle.calculationInFlight = false;
        handle.calculationStartedTick = Long.MIN_VALUE;
        handle.activeBatch = null;
        handle.publishAfterCalculation = false;
        authority.requestSimulationRetry(handle.level, batch);
    }

    private static void requestSnapshot(LevelHandle handle, String reason) {
        handle.snapshotPending = true;
        DataEngine.get().markDirty(
                SYSTEM_ID,
                snapshotObjectKey(handle.id),
                reason,
                UpdatePriority.NORMAL
        );
    }

    private static boolean isMatchingCalculation(
            MinecraftServer server,
            LevelHandle handle,
            WeatherAuthority.SimulationBatch batch,
            long submissionId
    ) {
        return isCurrent(server, handle)
                && handle.calculationInFlight
                && handle.submissionId == submissionId
                && handle.activeBatch == batch;
    }

    private static void clearCalculation(
            LevelHandle handle,
            WeatherAuthority.SimulationBatch batch,
            long submissionId
    ) {
        if (!handle.calculationInFlight
                || handle.submissionId != submissionId
                || handle.activeBatch != batch) {
            return;
        }
        handle.calculationInFlight = false;
        handle.calculationStartedTick = Long.MIN_VALUE;
        handle.activeBatch = null;
        handle.publishAfterCalculation = false;
    }

    private static long nextSubmissionId(LevelHandle handle) {
        handle.submissionId = handle.submissionId == Long.MAX_VALUE
                ? 1L
                : handle.submissionId + 1L;
        return handle.submissionId;
    }

    static boolean calculationTimedOut(long currentTick, long startedTick) {
        return startedTick != Long.MIN_VALUE
                && (currentTick < startedTick || currentTick - startedTick >= ASYNC_TIMEOUT_TICKS);
    }

    private static void publishDirtySnapshot(MinecraftServer server, long objectKey) {
        if (objectKey >= 0L || objectKey == Long.MIN_VALUE) {
            return;
        }
        LevelHandle handle = HANDLES_BY_ID.get(-objectKey);
        if (handle == null || !isCurrent(server, handle)) {
            return;
        }

        TickEngine.metrics().time(
                "weather",
                () -> WeatherAuthority.get().publishSnapshot(handle.level),
                server.getTickCount()
        );
        handle.snapshotPending = false;
    }

    private static LevelHandle handle(ServerLevel level) {
        LevelHandle existing = LEVEL_HANDLES.get(level.dimension());
        if (existing != null && existing.level == level) {
            return existing;
        }
        if (existing != null) {
            HANDLES_BY_ID.remove(existing.id);
        }
        if (nextLevelId == Long.MAX_VALUE) {
            throw new IllegalStateException("Weather level key space exhausted");
        }
        LevelHandle created = new LevelHandle(nextLevelId++, level);
        LEVEL_HANDLES.put(level.dimension(), created);
        HANDLES_BY_ID.put(created.id, created);
        return created;
    }

    private static boolean isCurrent(MinecraftServer server, LevelHandle handle) {
        return registeredServer == server
                && LEVEL_HANDLES.get(handle.level.dimension()) == handle
                && server.getLevel(handle.level.dimension()) == handle.level;
    }

    private static boolean hasRelevantPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && !player.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private static final class LevelHandle {
        private final long id;
        private final ServerLevel level;
        private boolean snapshotPending;
        private boolean calculationInFlight;
        private boolean publishAfterCalculation;
        private long calculationStartedTick = Long.MIN_VALUE;
        private long submissionId;
        private WeatherAuthority.SimulationBatch activeBatch;

        private LevelHandle(long id, ServerLevel level) {
            this.id = id;
            this.level = level;
        }
    }
}
