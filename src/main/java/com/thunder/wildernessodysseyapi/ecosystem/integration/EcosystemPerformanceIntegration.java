package com.thunder.wildernessodysseyapi.ecosystem.integration;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.dataengine.scheduler.UpdateFrequency;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.AdaptiveThrottle;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Adapts server-owned ecosystem maintenance to the Data and Tick engines.
 *
 * <p>Player-driven zone changes and AI restoration stay on the ordinary
 * server tick. Only periodic loaded-wildlife scans, abstract-population work,
 * and coalescible client refreshes enter the optional bounded pipeline.</p>
 */
public final class EcosystemPerformanceIntegration {
    public static final ResourceLocation SYSTEM_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "ecosystem_runtime"
    );

    static final int DATA_ENGINE_POLL_INTERVAL_TICKS = 5;

    private static long lastMaintenanceTick = Long.MIN_VALUE;

    private EcosystemPerformanceIntegration() {
    }

    /** Registers the first production Data Engine subsystem for a server lifecycle. */
    public static void register(DataEngine engine) {
        Objects.requireNonNull(engine, "Data Engine is required");
        lastMaintenanceTick = Long.MIN_VALUE;
        engine.registerSystem(DataSystemRegistration.builder(SYSTEM_ID)
                .frequency(UpdateFrequency.FAST)
                .intervalTicks(() -> DATA_ENGINE_POLL_INTERVAL_TICKS)
                .priority(UpdatePriority.BACKGROUND)
                .onScheduledUpdate(EcosystemPerformanceIntegration::runMaintenanceIfDue)
                .onDirtyUpdate((server, dirty) -> TickEngine.metrics().time(
                        "ecosystem",
                        () -> DistantWildlifeManager.get().syncDirtyPlayers(server),
                        server.getTickCount()
                ))
                .build());
    }

    /**
     * Coalesces lifecycle/config refreshes while retaining the existing packet
     * codec and server-authored distant-population state.
     */
    public static boolean markClientStateDirty(String reason) {
        DataEngine engine = DataEngine.get();
        if (!engine.isRunning() || !engine.isEnabled()) {
            return false;
        }
        return engine.markDirty(
                SYSTEM_ID,
                0L,
                Objects.requireNonNullElse(reason, "ecosystem client state changed"),
                UpdatePriority.NORMAL
        );
    }

    /** Preserves the old optional path when an operator disables the Data Engine. */
    public static void runFallbackIfDataEngineDisabled(
            MinecraftServer server,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        DataEngine engine = DataEngine.get();
        if (engine.isRunning() && engine.isEnabled()) {
            return;
        }
        if (serverHasTime.getAsBoolean()) {
            runMaintenanceIfDue(server);
        }
    }

    /** Clears the server-session cadence cursor. */
    public static void shutdown() {
        lastMaintenanceTick = Long.MIN_VALUE;
    }

    private static void runMaintenanceIfDue(MinecraftServer server) {
        long currentTick = server.getTickCount();
        ActivityLevel activity = hasRelevantPlayer(server) ? ActivityLevel.ACTIVE : ActivityLevel.DORMANT;
        int interval = maintenanceInterval(
                TickEngine.throttle(),
                TickEngine.pressure(),
                activity,
                TickEngine.recoveryMultiplier()
        );
        if (!TickEngine.throttle().shouldRun(currentTick, lastMaintenanceTick, interval)) {
            TickEngine.metrics().recordThrottled("ecosystem");
            return;
        }

        lastMaintenanceTick = currentTick;
        TickEngine.metrics().time("ecosystem", () -> {
            EcosystemSimulationManager.get().runOptionalMaintenance(server);
            DistantWildlifeManager.get().tick(server);
        }, currentTick);
    }

    static int maintenanceInterval(
            AdaptiveThrottle throttle,
            TickPressure pressure,
            ActivityLevel activity,
            double recoveryMultiplier
    ) {
        return throttle.intervalFor(
                "ecosystem",
                DATA_ENGINE_POLL_INTERVAL_TICKS,
                pressure,
                activity,
                recoveryMultiplier
        );
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
}
