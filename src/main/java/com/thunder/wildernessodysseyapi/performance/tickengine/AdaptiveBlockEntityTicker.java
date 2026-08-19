package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;

/**
 * Opt-in interval helper for expensive custom logic in WO-owned block entities.
 *
 * <p>The ordinary block-entity ticker must still run when Minecraft requires it;
 * callers guard only their optional scanning, modeling, or maintenance section.</p>
 */
public final class AdaptiveBlockEntityTicker {
    private AdaptiveBlockEntityTicker() {
    }

    /** Returns whether the custom section is due under current load and activity. */
    public static boolean shouldRun(
            AdaptiveThrottle throttle,
            String subsystem,
            long currentTick,
            long lastCustomWorkTick,
            Intervals intervals,
            TickPressure pressure,
            ActivityLevel activity,
            double recoveryMultiplier
    ) {
        int baseInterval = intervals.forPressure(pressure);
        // The explicit interval table already represents pressure; the shared
        // throttle adds only activity and gradual-recovery effects here.
        int effective = throttle.intervalFor(
                subsystem,
                baseInterval,
                TickPressure.RELAXED,
                activity,
                recoveryMultiplier
        );
        return throttle.shouldRun(currentTick, lastCustomWorkTick, effective);
    }

    /** Explicit intervals let each block entity choose its own gameplay-safe policy. */
    public record Intervals(int normal, int busy, int high, int critical, int overloaded) {
        public Intervals {
            normal = Math.max(1, normal);
            busy = Math.max(normal, busy);
            high = Math.max(busy, high);
            critical = Math.max(high, critical);
            overloaded = Math.max(critical, overloaded);
        }

        public int forPressure(TickPressure pressure) {
            return switch (pressure) {
                case RELAXED -> normal;
                case BUSY -> busy;
                case HIGH -> high;
                case CRITICAL -> critical;
                case OVERLOADED -> overloaded;
            };
        }
    }
}
