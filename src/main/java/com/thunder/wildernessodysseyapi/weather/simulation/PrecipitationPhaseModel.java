package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Selects natural rain or snow from temperature, biome climate, and season.
 *
 * <p>Wet-bulb temperature still controls whether flakes can survive, but an
 * ordinary biome also needs an active temperate winter. Permanently cold
 * biomes remain snow-capable in every season. Explicit operator snow commands
 * bypass this natural-weather policy.</p>
 */
public final class PrecipitationPhaseModel {

    private static final double COLD_BIOME_MAXIMUM_CELSIUS = 0.0;
    private static final double WINTER_SNOW_FACTOR_MINIMUM = 0.35;

    private PrecipitationPhaseModel() {
    }

    /** Returns the natural precipitation phase for one completed atmosphere step. */
    public static PrecipitationType classify(
            double intensity,
            double airTemperatureCelsius,
            double humidity,
            AtmosphereEnvironment environment
    ) {
        if (!Double.isFinite(intensity) || intensity <= 0.001) {
            return PrecipitationType.NONE;
        }
        AtmosphereEnvironment climate = environment == null
                ? AtmosphereEnvironment.TEMPERATE
                : environment;
        double wetBulbTemperature = AtmosphericThermodynamics.wetBulbTemperature(
                airTemperatureCelsius,
                humidity
        );
        return supportsNaturalSnow(climate)
                && wetBulbTemperature <= WeatherSample.SNOW_MAX_TEMPERATURE
                ? PrecipitationType.SNOW
                : PrecipitationType.RAIN;
    }

    /** Returns whether biome or calendar climate currently permits natural snow. */
    public static boolean supportsNaturalSnow(AtmosphereEnvironment environment) {
        AtmosphereEnvironment climate = environment == null
                ? AtmosphereEnvironment.TEMPERATE
                : environment;
        boolean permanentlyColdBiome = climate.biomeTemperatureCelsius()
                <= COLD_BIOME_MAXIMUM_CELSIUS;
        boolean temperateWinter = climate.seasonCalendarAvailable()
                && climate.snowSeasonFactor() >= WINTER_SNOW_FACTOR_MINIMUM;
        return permanentlyColdBiome || temperateWinter;
    }
}
