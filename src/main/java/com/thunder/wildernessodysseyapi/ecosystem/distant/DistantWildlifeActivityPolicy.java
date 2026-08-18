package com.thunder.wildernessodysseyapi.ecosystem.distant;

/** Low-cost schedule, weather, and disturbance response for an entire group. */
public final class DistantWildlifeActivityPolicy {

    private DistantWildlifeActivityPolicy() {
    }

    /**
     * Returns a bounded group movement multiplier without running individual AI.
     *
     * <p>Future schedule and migration systems can replace or extend this policy
     * while retaining the same group movement and synchronization contract.</p>
     */
    public static double movementScale(
            boolean nocturnal,
            long dayTime,
            boolean weatherSensitive,
            double precipitationIntensity,
            double thunderIntensity,
            double disturbanceIntensity
    ) {
        long timeOfDay = Math.floorMod(dayTime, 24_000L);
        boolean twilight = timeOfDay < 1_000L
                || (timeOfDay >= 11_000L && timeOfDay < 14_000L)
                || timeOfDay >= 23_000L;
        boolean daylight = timeOfDay >= 1_000L && timeOfDay < 12_000L;
        boolean normallyActive = nocturnal ? !daylight : daylight;
        double scheduleScale = twilight ? 0.70 : normallyActive ? 1.0 : 0.28;

        double weatherSeverity = Math.max(
                unit(precipitationIntensity),
                unit(thunderIntensity) * 1.15
        );
        double weatherScale = weatherSensitive
                ? Math.max(0.18, 1.0 - weatherSeverity * 0.82)
                : Math.max(0.55, 1.0 - weatherSeverity * 0.35);
        double disturbanceBoost = 1.0 + unit(disturbanceIntensity) * 0.75;
        return Math.max(0.0, Math.min(2.0, scheduleScale * weatherScale * disturbanceBoost));
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
