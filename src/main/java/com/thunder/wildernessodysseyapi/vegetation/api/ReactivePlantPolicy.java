package com.thunder.wildernessodysseyapi.vegetation.api;

/** Pure decision helpers shared by scheduled and vanilla-random-tick reactions. */
public final class ReactivePlantPolicy {

    /** Storm strength at which weather-reactive flowers close. */
    public static final double SEVERE_FLOWER_STORM_THRESHOLD = 0.62;

    private ReactivePlantPolicy() {
    }

    /** Returns whether a registered flower should present its open state. */
    public static boolean flowerShouldOpen(
            VegetationClimateState climate,
            boolean suitableDaylight,
            boolean closeAtNight,
            boolean closeInSevereWeather
    ) {
        VegetationClimateState safe = climate == null ? VegetationClimateState.DEFAULT : climate;
        if (closeAtNight && !suitableDaylight) {
            return false;
        }
        return !closeInSevereWeather
                || safe.stormIntensity() < SEVERE_FLOWER_STORM_THRESHOLD;
    }
}
