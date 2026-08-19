package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;

/**
 * Opt-in helper for expensive WO entity AI decisions and environment scans.
 *
 * <p>Never use this helper to skip base {@code Entity.tick()}, physics, combat
 * safety, or other Minecraft-owned behavior.</p>
 */
public final class AdaptiveEntityWork {
    private AdaptiveEntityWork() {
    }

    /** Returns whether one optional custom entity calculation is due. */
    public static boolean shouldRun(
            AdaptiveThrottle throttle,
            String subsystem,
            long currentTick,
            long lastCustomWorkTick,
            int normalIntervalTicks,
            TickPressure pressure,
            ActivityLevel activity,
            double recoveryMultiplier
    ) {
        int interval = throttle.intervalFor(
                subsystem,
                normalIntervalTicks,
                pressure,
                activity,
                recoveryMultiplier
        );
        return throttle.shouldRun(currentTick, lastCustomWorkTick, interval);
    }
}
