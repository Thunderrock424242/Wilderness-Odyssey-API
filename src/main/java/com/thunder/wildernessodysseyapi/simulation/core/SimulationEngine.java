package com.thunder.wildernessodysseyapi.simulation.core;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.dataengine.scheduler.UpdateFrequency;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.SubsystemPolicy;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPriority;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationContext;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationSystem;
import com.thunder.wildernessodysseyapi.simulation.debug.SimulationDebugSnapshot;
import com.thunder.wildernessodysseyapi.simulation.event.SimulationEvent;
import com.thunder.wildernessodysseyapi.simulation.event.SimulationEventDispatcher;
import com.thunder.wildernessodysseyapi.simulation.integration.EcosystemSimulationBridge;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegion;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegionManager;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Server-lifecycle owner of optional regional simulation orchestration.
 *
 * <p>The engine discovers only player-occupied or explicitly requested cells,
 * composes immutable owner snapshots, and invokes registered adapters through
 * the existing Data and Tick engines. It does not tick authoritative weather,
 * water, wildlife, vegetation, meteor, or Riftfall state.</p>
 */
public final class SimulationEngine {
    public static final ResourceLocation DATA_ENGINE_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "simulation_orchestration"
    );

    private static final String TICK_SUBSYSTEM_ID = "simulation";
    private static final int DATA_ENGINE_POLL_TICKS = 5;
    private static final int NORMAL_PASS_INTERVAL_TICKS = 20;
    private static final int MAXIMUM_REGIONS_PER_PASS = 16;
    private static final SimulationEngine INSTANCE = new SimulationEngine();

    private final SimulationRegistry registry = new SimulationRegistry();
    private final SimulationRegionManager regions = new SimulationRegionManager();
    private final SimulationEventDispatcher events = new SimulationEventDispatcher();
    private final SimulationCoordinator coordinator = new SimulationCoordinator(registry, regions);
    private final Map<ResourceLocation, SystemCounters> systemCounters = new LinkedHashMap<>();
    private final SimulationCoordinator.SystemObserver observer = new MetricsObserver();

    private MinecraftServer server;
    private long lastPassTick = Long.MIN_VALUE;
    private long lastPassNanos;
    private long processedRegions;
    private long deferredPasses;
    private long systemFailures;
    private long eventsDispatched;
    private long eventListenerFailures;
    private int lastEnabledSystems;

    private SimulationEngine() {
    }

    /** Returns the process registry/lifecycle owner. */
    public static SimulationEngine get() {
        return INSTANCE;
    }

    /** Registers one system definition and starts it immediately for a live server. */
    public synchronized void registerSystem(SimulationSystem system) {
        registry.register(system);
        systemCounters.put(system.id(), new SystemCounters());
        MinecraftServer currentServer = server;
        if (currentServer != null) {
            Runnable startHook = () -> startLateRegistration(currentServer, system);
            if (currentServer.isSameThread()) {
                startHook.run();
            } else {
                currentServer.execute(startHook);
            }
        }
    }

    /** Registers one typed event listener behind the shared consequence seam. */
    public <T extends SimulationEvent> void registerEventListener(
            ResourceLocation listenerId,
            Class<T> eventType,
            Consumer<T> listener
    ) {
        events.register(listenerId, eventType, listener);
    }

    /** Creates clean runtime state and registers the optional Data Engine lane. */
    public synchronized void start(MinecraftServer server, DataEngine dataEngine) {
        Objects.requireNonNull(server, "Minecraft server is required");
        Objects.requireNonNull(dataEngine, "Data Engine is required");
        if (this.server != null) {
            shutdown();
        }
        this.server = server;
        resetMetrics();
        regions.clearAll();
        TickEngine.registerSubsystem(new SubsystemPolicy(
                TICK_SUBSYSTEM_ID,
                "Simulation",
                TickPriority.BACKGROUND,
                200,
                true
        ));
        dataEngine.registerSystem(DataSystemRegistration.builder(DATA_ENGINE_ID)
                .frequency(UpdateFrequency.FAST)
                .intervalTicks(() -> DATA_ENGINE_POLL_TICKS)
                .priority(UpdatePriority.BACKGROUND)
                .onScheduledUpdate(this::runScheduledPass)
                .build());
        registry.onServerStarted(server, this::recordFailure);
        lastEnabledSystems = registry.enabledSystemCount();
    }

    /** Queues one cell without scanning or immediately touching owner state. */
    public synchronized boolean requestRegion(
            ServerLevel level,
            BlockPos position,
            SimulationTrigger trigger
    ) {
        if (server == null || level == null || position == null || trigger == null) {
            return false;
        }
        SimulationRegion region = EcosystemSimulationBridge.regionAt(level, position);
        return regions.request(region, trigger, level.getGameTime()).accepted();
    }

    /** Dispatches a typed fact immediately and queues only optional follow-up work. */
    public synchronized SimulationEventDispatcher.DispatchResult publish(
            ServerLevel level,
            SimulationEvent event
    ) {
        Objects.requireNonNull(level, "Server level is required");
        Objects.requireNonNull(event, "Simulation event is required");
        if (server == null || !event.dimension().equals(level.dimension().location())) {
            return new SimulationEventDispatcher.DispatchResult(0, 0);
        }
        SimulationEventDispatcher.DispatchResult result = events.dispatch(event, (listenerId, exception) -> {
            eventListenerFailures++;
            logFailure(listenerId, "event dispatch", exception, eventListenerFailures);
        });
        eventsDispatched++;
        lastEnabledSystems = registry.enabledSystemCount();
        if (lastEnabledSystems > 0) {
            requestRegion(level, event.position(), SimulationTrigger.WORLD_DISTURBANCE);
        }
        return result;
    }

    /** Re-evaluates dynamic enablement and lifecycle-backed config state. */
    public synchronized void onConfigurationReload() {
        if (server == null) {
            return;
        }
        lastPassTick = Long.MIN_VALUE;
        registry.onConfigurationReload(this::recordFailure);
        lastEnabledSystems = registry.enabledSystemCount();
    }

    /** Releases one dimension's transient requests/states and notifies participants. */
    public synchronized void unload(ServerLevel level) {
        if (level == null) {
            return;
        }
        ResourceLocation dimension = level.dimension().location();
        regions.clearDimension(dimension);
        registry.onLevelUnload(dimension, this::recordFailure);
    }

    /** Releases every runtime reference while preserving registration definitions. */
    public synchronized void shutdown() {
        if (server == null) {
            return;
        }
        registry.onServerStopping(this::recordFailure);
        regions.clearAll();
        server = null;
        lastPassTick = Long.MIN_VALUE;
        lastEnabledSystems = 0;
    }

    /** Builds immutable command/F3 diagnostics outside the update hot path. */
    public synchronized SimulationDebugSnapshot debugSnapshot() {
        SimulationRegionManager.Diagnostics regional = regions.diagnostics();
        Map<ResourceLocation, SimulationDebugSnapshot.SystemSnapshot> systems = new LinkedHashMap<>();
        for (SimulationSystem system : registry.systems()) {
            SystemCounters counters = systemCounters.computeIfAbsent(system.id(), ignored -> new SystemCounters());
            systems.put(system.id(), counters.snapshot());
        }
        return new SimulationDebugSnapshot(
                server != null,
                registry.systems().size(),
                lastEnabledSystems,
                events.listenerIds().size(),
                regional.pendingRegions(),
                regional.trackedRegions(),
                regional.activeRegions(),
                regional.nearbyRegions(),
                regional.backgroundRegions(),
                regional.dormantRegions(),
                regional.acceptedRequests(),
                regional.coalescedRequests(),
                regional.rejectedRequests(),
                processedRegions,
                deferredPasses,
                systemFailures,
                eventsDispatched,
                eventListenerFailures,
                Math.max(0L, lastPassTick),
                lastPassNanos,
                server == null ? TickPressure.RELAXED : TickEngine.pressure(),
                systems
        );
    }

    private void runScheduledPass(MinecraftServer currentServer) {
        synchronized (this) {
            if (server != currentServer) {
                return;
            }
            lastEnabledSystems = registry.enabledSystemCount();
            if (lastEnabledSystems == 0) {
                return;
            }
            long currentTick = currentServer.getTickCount();
            ActivityLevel activity = hasRelevantPlayer(currentServer)
                    ? ActivityLevel.ACTIVE : ActivityLevel.DORMANT;
            int interval = TickEngine.throttle().intervalFor(
                    TICK_SUBSYSTEM_ID,
                    NORMAL_PASS_INTERVAL_TICKS,
                    TickEngine.pressure(),
                    activity,
                    TickEngine.recoveryMultiplier()
            );
            if (!TickEngine.throttle().shouldRun(currentTick, lastPassTick, interval)) {
                deferredPasses++;
                TickEngine.metrics().recordThrottled(TICK_SUBSYSTEM_ID);
                return;
            }

            lastPassTick = currentTick;
            queueSystemRegions(currentServer);
            queuePlayerRegions(currentServer);
            int maximumRegions = regionLimit(TickEngine.pressure());
            if (maximumRegions == 0) {
                deferredPasses++;
                return;
            }
            long started = System.nanoTime();
            SimulationCoordinator.ProcessingReport report = coordinator.process(
                    currentServer,
                    maximumRegions,
                    observer
            );
            lastPassNanos = Math.max(0L, System.nanoTime() - started);
            TickEngine.metrics().recordExecution(TICK_SUBSYSTEM_ID, lastPassNanos, currentTick);
            processedRegions += report.processedRegions();
        }
    }

    private void queueSystemRegions(MinecraftServer currentServer) {
        registry.collectRegions(
                currentServer,
                (level, position) -> requestRegion(
                        level,
                        position,
                        SimulationTrigger.SYSTEM_RELEVANCE
                ),
                this::recordFailure
        );
    }

    private void queuePlayerRegions(MinecraftServer currentServer) {
        for (ServerLevel level : currentServer.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isAlive() && !player.isSpectator()) {
                    requestRegion(level, player.blockPosition(), SimulationTrigger.PLAYER_INTEREST);
                }
            }
        }
    }

    private static boolean hasRelevantPlayer(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isAlive() && !player.isSpectator()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int regionLimit(TickPressure pressure) {
        return switch (pressure) {
            case RELAXED -> MAXIMUM_REGIONS_PER_PASS;
            case BUSY -> 8;
            case HIGH -> 4;
            case CRITICAL, OVERLOADED -> 0;
        };
    }

    private void resetMetrics() {
        lastPassTick = Long.MIN_VALUE;
        lastPassNanos = 0L;
        processedRegions = 0L;
        deferredPasses = 0L;
        systemFailures = 0L;
        eventsDispatched = 0L;
        eventListenerFailures = 0L;
        lastEnabledSystems = 0;
        systemCounters.clear();
        for (SimulationSystem system : registry.systems()) {
            systemCounters.put(system.id(), new SystemCounters());
        }
    }

    private void recordFailure(ResourceLocation systemId, String phase, RuntimeException exception) {
        systemFailures++;
        SystemCounters counters = systemCounters.computeIfAbsent(systemId, ignored -> new SystemCounters());
        counters.failures++;
        logFailure(systemId, phase, exception, counters.failures);
    }

    private synchronized void startLateRegistration(
            MinecraftServer expectedServer,
            SimulationSystem system
    ) {
        if (server != expectedServer) {
            return;
        }
        try {
            system.onServerStarted(expectedServer);
        } catch (RuntimeException exception) {
            recordFailure(system.id(), "late server start", exception);
        }
        lastEnabledSystems = registry.enabledSystemCount();
    }

    private static void logFailure(
            ResourceLocation id,
            String phase,
            Exception exception,
            long failureCount
    ) {
        if (failureCount == 1L || failureCount % 100L == 0L) {
            ModConstants.LOGGER.error(
                    "[Simulation] Isolated {} failure for {} (occurrence {})",
                    phase,
                    id,
                    failureCount,
                    exception
            );
        }
    }

    private final class MetricsObserver implements SimulationCoordinator.SystemObserver {
        @Override
        public void onSystemUpdated(SimulationSystem system, long elapsedNanos) {
            SystemCounters counters = systemCounters.computeIfAbsent(system.id(), ignored -> new SystemCounters());
            counters.updates++;
            counters.lastNanos = elapsedNanos;
            counters.totalNanos += elapsedNanos;
        }

        @Override
        public void onSystemSkipped(SimulationSystem system) {
            systemCounters.computeIfAbsent(system.id(), ignored -> new SystemCounters()).skipped++;
        }

        @Override
        public void onSystemFailure(SimulationSystem system, String phase, Exception exception) {
            systemFailures++;
            SystemCounters counters = systemCounters.computeIfAbsent(system.id(), ignored -> new SystemCounters());
            counters.failures++;
            logFailure(system.id(), phase, exception, counters.failures);
        }

        @Override
        public void onRegionProcessed(
                SimulationContext context,
                SimulationCoordinator.SystemDispatchReport report
        ) {
            // Region totals are added once by the enclosing bounded pass.
        }
    }

    private static final class SystemCounters {
        private long updates;
        private long skipped;
        private long failures;
        private long totalNanos;
        private long lastNanos;

        private SimulationDebugSnapshot.SystemSnapshot snapshot() {
            return new SimulationDebugSnapshot.SystemSnapshot(
                    updates,
                    skipped,
                    failures,
                    totalNanos,
                    lastNanos
            );
        }
    }
}
