package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;

/**
 * Pure scheduling rules for dormant, recently active, and persistent-storm cells.
 *
 * <p>Keeping this policy separate makes inactivity behavior testable without a
 * loaded Minecraft world and ensures catch-up work remains strictly bounded.</p>
 */
public final class AtmosphereActivityPolicy {

    private AtmosphereActivityPolicy() {
    }

    /** Returns whether grace or storm persistence keeps a cell scheduled. */
    public static boolean shouldSimulate(
            AtmosphereView view,
            long gameTime,
            long gracePeriodTicks,
            double persistentStormThreshold
    ) {
        if (view == null) {
            return false;
        }
        return view.sample().stormEnergy() >= clamp01(persistentStormThreshold)
                || inactiveAge(view, gameTime) <= Math.max(0L, gracePeriodTicks);
    }

    /** Returns the non-negative number of ticks since player activity. */
    public static long inactiveAge(AtmosphereView view, long gameTime) {
        return view == null ? Long.MAX_VALUE
                : Math.max(0L, Math.max(0L, gameTime) - view.lastActiveTick());
    }

    /** Returns a bounded number of lightweight updates for a reactivated cell. */
    public static int catchUpSteps(
            AtmosphereView view,
            long gameTime,
            int simulationIntervalTicks,
            int maximumSteps
    ) {
        if (view == null) {
            return 0;
        }
        long elapsed = Math.max(1L, Math.max(0L, gameTime) - view.lastSimulatedTick());
        long intervals = Math.max(1L, elapsed / Math.max(1, simulationIntervalTicks));
        return (int) Math.min(Math.max(1, maximumSteps), intervals);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
