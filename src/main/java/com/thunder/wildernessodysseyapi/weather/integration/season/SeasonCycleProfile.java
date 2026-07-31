package com.thunder.wildernessodysseyapi.weather.integration.season;

import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;

import java.util.Locale;

/**
 * Converts an external calendar position into bounded atmospheric influences.
 *
 * <p>The profile is intentionally independent of either season mod. Adapters
 * only translate their calendar into a normalized cycle or tropical phase,
 * keeping balance and regression tests under Wilderness ownership.</p>
 */
public final class SeasonCycleProfile {

    private SeasonCycleProfile() {
    }

    /** Builds a smooth temperate-year profile from a wrapped {@code [0, 1)} phase. */
    public static SeasonalWeatherInfluence.SeasonalOffset temperate(
            double cyclePhase,
            WeatherConfig.SeasonSettings settings
    ) {
        WeatherConfig.SeasonSettings controls =
                settings == null ? WeatherConfig.SeasonSettings.DEFAULT : settings;
        if (!controls.enabled()) {
            return SeasonalWeatherInfluence.SeasonalOffset.NONE;
        }

        double phase = wrap01(cyclePhase);
        // Atmospheric temperature lags the solstices: midsummer and midwinter
        // become the warmest and coldest portions of the simulated year.
        double temperatureWave = Math.cos(Math.PI * 2.0 * (phase - 0.375));
        double moistureWave = Math.sin(Math.PI * 2.0 * (phase + 0.05));
        double warmConvection = Math.max(0.0, temperatureWave) * 0.72
                + Math.max(0.0, moistureWave) * 0.28;
        double coldStability = Math.max(0.0, -temperatureWave) * 0.38;

        return new SeasonalWeatherInfluence.SeasonalOffset(
                temperatureWave * controls.temperatureAmplitudeCelsius(),
                moistureWave * controls.humidityAmplitude(),
                (warmConvection - coldStability) * controls.storminessAmplitude(),
                1.0 + temperatureWave * 0.18
        );
    }

    /** Builds the wet/dry profile used by Serene Seasons tropical biomes. */
    public static SeasonalWeatherInfluence.SeasonalOffset tropical(
            String tropicalSeason,
            WeatherConfig.SeasonSettings settings
    ) {
        WeatherConfig.SeasonSettings controls =
                settings == null ? WeatherConfig.SeasonSettings.DEFAULT : settings;
        if (!controls.enabled() || tropicalSeason == null) {
            return SeasonalWeatherInfluence.SeasonalOffset.NONE;
        }

        String name = tropicalSeason.toUpperCase(Locale.ROOT);
        boolean wet = name.contains("WET");
        boolean dry = name.contains("DRY");
        if (!wet && !dry) {
            return SeasonalWeatherInfluence.SeasonalOffset.NONE;
        }
        double intensity = name.contains("MID") ? 1.0 : 0.72;
        if (wet) {
            return new SeasonalWeatherInfluence.SeasonalOffset(
                    -controls.temperatureAmplitudeCelsius() * 0.12 * intensity,
                    controls.humidityAmplitude() * intensity,
                    controls.storminessAmplitude() * 0.85 * intensity,
                    0.92
            );
        }
        return new SeasonalWeatherInfluence.SeasonalOffset(
                controls.temperatureAmplitudeCelsius() * 0.18 * intensity,
                -controls.humidityAmplitude() * intensity,
                -controls.storminessAmplitude() * 0.55 * intensity,
                1.16
        );
    }

    private static double wrap01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return value - Math.floor(value);
    }
}
