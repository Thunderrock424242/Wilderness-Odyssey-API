package com.thunder.wildernessodysseyapi.vegetation.api;

/**
 * Immutable chunk-level climate consulted by reactive vegetation.
 *
 * <p>All continuous fields use normalized {@code [0, 1]} units. The state is
 * updated from localized weather only when the owning loaded chunk reaches its
 * scheduled turn; individual plants never query the weather authority.</p>
 *
 * @param moisture retained regional plant-available moisture
 * @param recentRainfall smoothed recent liquid precipitation
 * @param droughtLevel retained vegetation drought stress
 * @param stormIntensity current severe-weather pressure on plants
 * @param seasonState coarse plant-relevant external calendar state
 * @param lastClimateUpdateTick last server tick that advanced regional climate
 * @param lastVegetationUpdateTick last server tick that sampled plants
 * @param plantsProcessed registered plants processed in the last regional pass
 * @param averageProcessingMicros smoothed regional-pass processing time
 */
public record VegetationClimateState(
        double moisture,
        double recentRainfall,
        double droughtLevel,
        double stormIntensity,
        VegetationSeasonState seasonState,
        long lastClimateUpdateTick,
        long lastVegetationUpdateTick,
        int plantsProcessed,
        double averageProcessingMicros
) {

    /** Temperate initial state used before the first loaded-chunk sample. */
    public static final VegetationClimateState DEFAULT = new VegetationClimateState(
            0.5,
            0.0,
            0.0,
            0.0,
            VegetationSeasonState.UNKNOWN,
            0L,
            0L,
            0,
            0.0
    );

    /** Normalizes persisted, networked, and compatibility-provided values. */
    public VegetationClimateState {
        moisture = unit(moisture);
        recentRainfall = unit(recentRainfall);
        droughtLevel = unit(droughtLevel);
        stormIntensity = unit(stormIntensity);
        seasonState = seasonState == null ? VegetationSeasonState.UNKNOWN : seasonState;
        lastClimateUpdateTick = Math.max(0L, lastClimateUpdateTick);
        lastVegetationUpdateTick = Math.max(0L, lastVegetationUpdateTick);
        plantsProcessed = Math.max(0, plantsProcessed);
        averageProcessingMicros = Math.max(
                0.0,
                Double.isFinite(averageProcessingMicros) ? averageProcessingMicros : 0.0
        );
    }

    /**
     * Returns normalized post-rain mushroom favorability.
     *
     * <p>Recent rainfall is weighted most heavily, while retained moisture
     * helps opportunities persist briefly after the storm ends.</p>
     */
    public double mushroomOpportunity() {
        return unit(recentRainfall * 0.62 + moisture * 0.48 - droughtLevel * 0.55 - 0.20);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
