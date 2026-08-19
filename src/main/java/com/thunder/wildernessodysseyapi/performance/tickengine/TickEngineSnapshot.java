package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.Map;

/** Immutable read-only Tick Engine state for debug UI and external diagnostics. */
public record TickEngineSnapshot(
        boolean enabled,
        double tps,
        double currentMspt,
        double shortAverageMspt,
        double mediumAverageMspt,
        double recentMaximumMspt,
        long tickCount,
        long overloadedTickCount,
        int consecutiveOverloadedTicks,
        TickPressure pressure,
        double recoveryMultiplier,
        double optionalBudgetMillis,
        double optionalWorkMillis,
        double optionalBudgetRemainingMillis,
        int deferredTasks,
        double deferredQueuePressure,
        int backgroundQueuedTasks,
        double backgroundQueuePressure,
        int throttledSubsystems,
        String worstSubsystem,
        Map<String, SubsystemTiming.Snapshot> subsystemTimings
) {
}
