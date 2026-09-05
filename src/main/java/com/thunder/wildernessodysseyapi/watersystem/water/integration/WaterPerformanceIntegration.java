package com.thunder.wildernessodysseyapi.watersystem.water.integration;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import com.thunder.wildernessodysseyapi.dataengine.queue.QueuedUpdate;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.dataengine.scheduler.UpdateFrequency;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.AdaptiveThrottle;
import com.thunder.wildernessodysseyapi.performance.tickengine.SubsystemPolicy;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaStateField;
import com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WeatherHydrologyManager;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.network.OceanSeaStateSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.network.SphSnapshotSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.network.WaterVolumeSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.network.WatershedSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Adapts optional server-owned water maintenance to the Data and Tick engines.
 *
 * <p>The water subsystem's existing managers remain authoritative. This class
 * only supplies one central cadence and separate coalescing keys for regional
 * hydrology, shoreline flow, network publication, and periodic SPH persistence.
 * Gameplay-critical SPH collision, motion, and settlement remain on their
 * direct server tick because they read and mutate live Minecraft state.</p>
 */
public final class WaterPerformanceIntegration {
    public static final ResourceLocation SYSTEM_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "water_runtime"
    );

    static final int DATA_ENGINE_POLL_INTERVAL_TICKS = 1;
    static final int VOLUME_SNAPSHOT_INTERVAL_TICKS = 10;
    static final int REGIONAL_NETWORK_INTERVAL_TICKS = 20;

    private static final int TASK_KEY_STRIDE = 8;
    private static final Map<ResourceKey<Level>, LevelHandle> LEVEL_HANDLES = new HashMap<>();

    private static MinecraftServer registeredServer;
    private static long nextLevelId = 1L;

    private WaterPerformanceIntegration() {
    }

    /** Registers optional water maintenance for one server lifecycle. */
    public static void register(DataEngine engine, MinecraftServer server) {
        Objects.requireNonNull(engine, "Data Engine is required");
        registeredServer = Objects.requireNonNull(server, "Minecraft server is required");
        LEVEL_HANDLES.clear();
        nextLevelId = 1L;

        engine.registerSystem(DataSystemRegistration.builder(SYSTEM_ID)
                .frequency(UpdateFrequency.EVERY_TICK)
                .intervalTicks(WaterPerformanceIntegration::currentPollIntervalTicks)
                .priority(UpdatePriority.NORMAL)
                .onScheduledUpdate(WaterPerformanceIntegration::enqueueMaintenance)
                .build());
    }

    /**
     * Preserves the previous synchronous water path when Data Engine is off.
     *
     * <p>The live server allowance is rechecked between every optional unit.
     * Missed work remains due but is never replayed more than once per unit.</p>
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

        long currentTick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            LevelHandle handle = handle(level);
            for (WaterTask task : WaterTask.values()) {
                if (!task.isDue(handle, currentTick)) {
                    continue;
                }
                if (!serverHasTime.getAsBoolean()) {
                    return;
                }
                runTask(server, handle, task);
            }
        }
    }

    /** Removes session-local task state for an unloading level. */
    public static void forgetLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        LevelHandle handle = LEVEL_HANDLES.get(level.dimension());
        if (handle != null && handle.level == level) {
            LEVEL_HANDLES.remove(level.dimension());
        }
    }

    /** Releases all server and level references before Data Engine shutdown. */
    public static void shutdown() {
        registeredServer = null;
        LEVEL_HANDLES.clear();
        nextLevelId = 1L;
    }

    static int pollInterval(
            AdaptiveThrottle throttle,
            TickPressure pressure,
            ActivityLevel activity,
            double recoveryMultiplier
    ) {
        SubsystemPolicy policy = throttle.policy("water");
        int governedBase = policy == null
                ? DATA_ENGINE_POLL_INTERVAL_TICKS
                : Math.min(DATA_ENGINE_POLL_INTERVAL_TICKS, policy.maximumIntervalTicks());
        return throttle.intervalFor(
                "water",
                governedBase,
                pressure,
                activity,
                recoveryMultiplier
        );
    }

    static boolean isElapsedDue(long currentTick, long lastCompletedTick, int intervalTicks) {
        return lastCompletedTick == Long.MIN_VALUE
                || currentTick < lastCompletedTick
                || currentTick - lastCompletedTick >= Math.max(1, intervalTicks);
    }

    static long taskKey(long levelId, int taskLane) {
        if (levelId <= 0L || levelId > Long.MAX_VALUE / TASK_KEY_STRIDE) {
            throw new IllegalArgumentException("Water level id is outside the task-key range");
        }
        if (taskLane < 0 || taskLane >= TASK_KEY_STRIDE) {
            throw new IllegalArgumentException("Water task lane is outside the coalescing range");
        }
        return levelId * TASK_KEY_STRIDE + taskLane;
    }

    private static int currentPollIntervalTicks() {
        return pollInterval(
                TickEngine.throttle(),
                TickEngine.pressure(),
                ActivityLevel.ACTIVE,
                TickEngine.recoveryMultiplier()
        );
    }

    private static void enqueueMaintenance(MinecraftServer server) {
        DataEngine engine = DataEngine.get();
        long currentTick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            LevelHandle handle = handle(level);
            for (WaterTask task : WaterTask.values()) {
                if (!task.isDue(handle, currentTick)) {
                    continue;
                }
                engine.submit(QueuedUpdate.dirty(
                        SYSTEM_ID,
                        taskKey(handle.id, task.lane),
                        task.priority,
                        currentTick,
                        () -> runTask(server, handle, task)
                ));
            }
        }
    }

    private static void runTask(MinecraftServer server, LevelHandle handle, WaterTask task) {
        if (!isCurrent(server, handle)) {
            return;
        }
        long currentTick = server.getTickCount();
        if (!task.isDue(handle, currentTick)) {
            return;
        }
        TickEngine.metrics().time(
                "water",
                () -> task.run(handle.level),
                currentTick
        );
        task.markCompleted(handle, currentTick);
    }

    private static LevelHandle handle(ServerLevel level) {
        LevelHandle existing = LEVEL_HANDLES.get(level.dimension());
        if (existing != null && existing.level == level) {
            return existing;
        }
        if (nextLevelId > Long.MAX_VALUE / TASK_KEY_STRIDE) {
            throw new IllegalStateException("Water level key space exhausted");
        }
        LevelHandle created = new LevelHandle(nextLevelId++, level);
        LEVEL_HANDLES.put(level.dimension(), created);
        return created;
    }

    private static boolean isCurrent(MinecraftServer server, LevelHandle handle) {
        return registeredServer == server
                && LEVEL_HANDLES.get(handle.level.dimension()) == handle
                && server.getLevel(handle.level.dimension()) == handle.level;
    }

    private enum WaterTask {
        REGIONAL(0, UpdatePriority.NORMAL, 1) {
            @Override
            void run(ServerLevel level) {
                OceanSeaStateField.tickLevel(level);
                WatershedSimulationManager.tickLevel(level);
                if (!WaterSimulationConfig.watershedSimulationEnabled()) {
                    WeatherHydrologyManager.tickLevel(level);
                }
            }
        },
        SHORELINE(1, UpdatePriority.NORMAL, 1) {
            @Override
            void run(ServerLevel level) {
                if (!level.players().isEmpty()) {
                    ShorelineWaterManager.get().tick(level);
                }
            }
        },
        SPH_SNAPSHOT(2, UpdatePriority.NORMAL, SPHConstants.NETWORK_SNAPSHOT_INTERVAL_TICKS) {
            @Override
            void run(ServerLevel level) {
                if (WildernessWaterRules.isEnabled(level)) {
                    SphSnapshotSynchronizer.syncLevel(level);
                }
            }
        },
        VOLUME_SNAPSHOT(3, UpdatePriority.NORMAL, VOLUME_SNAPSHOT_INTERVAL_TICKS) {
            @Override
            void run(ServerLevel level) {
                if (WildernessWaterRules.isEnabled(level)) {
                    WaterVolumeSynchronizer.syncLevel(level);
                }
            }
        },
        REGIONAL_NETWORK(4, UpdatePriority.NORMAL, REGIONAL_NETWORK_INTERVAL_TICKS) {
            @Override
            void run(ServerLevel level) {
                OceanSeaStateSynchronizer.syncLevel(level);
                WatershedSynchronizer.syncLevel(level);
            }
        },
        EROSION(6, UpdatePriority.LOW, 20) {
            @Override
            void run(ServerLevel level) {
                com.thunder.wildernessodysseyapi.watersystem.water.erosion.ErosionManager.tick(level);
            }
        },
        PERSISTENCE(5, UpdatePriority.LOW, SPHConstants.PERSISTENCE_CAPTURE_INTERVAL_TICKS) {
            @Override
            void run(ServerLevel level) {
                if (WildernessWaterRules.isEnabled(level)) {
                    SPHSimulationManager.get().capturePersistentLevel(level);
                }
            }
        };

        private final int lane;
        private final UpdatePriority priority;
        private final int intervalTicks;

        WaterTask(int lane, UpdatePriority priority, int intervalTicks) {
            this.lane = lane;
            this.priority = priority;
            this.intervalTicks = intervalTicks;
        }

        abstract void run(ServerLevel level);

        private boolean isDue(LevelHandle handle, long currentTick) {
            return isElapsedDue(currentTick, handle.lastCompleted[lane], intervalTicks);
        }

        private void markCompleted(LevelHandle handle, long currentTick) {
            handle.lastCompleted[lane] = currentTick;
        }
    }

    private static final class LevelHandle {
        private final long id;
        private final ServerLevel level;
        private final long[] lastCompleted = new long[TASK_KEY_STRIDE];

        private LevelHandle(long id, ServerLevel level) {
            this.id = id;
            this.level = level;
            java.util.Arrays.fill(lastCompleted, Long.MIN_VALUE);
        }
    }
}
