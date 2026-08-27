package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBody;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Converts localized weather into a conservative fixed-point water flux.
 *
 * <p>Positive values credit rain or thaw; negative values debit evaporation.
 * Large oceans are intentionally neutral because ordinary weather must not
 * raise or drain an effectively infinite Minecraft ocean column by column.</p>
 */
public final class WaterCycleFluxModel {

    private WaterCycleFluxModel() {
    }

    /** Returns signed authority units contributed by one chunk-scale probe. */
    public static double fluxUnits(
            WeatherSample weather,
            WaterBody.Kind kind,
            double maximumRainUnits,
            double maximumEvaporationUnits
    ) {
        WeatherSample safeWeather = weather == null ? WeatherSample.CLEAR : weather;
        WaterBody.Kind safeKind = kind == null ? WaterBody.Kind.LOCAL_VOLUME : kind;
        if (safeKind == WaterBody.Kind.LARGE_OCEAN
                || safeKind == WaterBody.Kind.LARGE_COAST) {
            return 0.0;
        }

        double rainMultiplier = switch (safeKind) {
            case LARGE_RIVER -> 0.72;
            case LARGE_POND, LARGE_LAKE, LOCAL_VOLUME -> 1.0;
            case LARGE_OCEAN, LARGE_COAST -> 0.0;
        };
        double evaporationMultiplier = switch (safeKind) {
            case LARGE_RIVER -> 0.45;
            case LARGE_POND, LARGE_LAKE, LOCAL_VOLUME -> 1.0;
            case LARGE_OCEAN, LARGE_COAST -> 0.0;
        };

        boolean liquidPrecipitation = safeWeather.precipitationType() == PrecipitationType.RAIN
                || safeWeather.precipitationType() == PrecipitationType.HAIL;
        double precipitation = liquidPrecipitation
                ? safeWeather.precipitationIntensity()
                : 0.0;
        double rain = Math.max(0.0, maximumRainUnits)
                * precipitation
                * rainMultiplier;

        // Stored snow contributes only while above freezing. This makes thaw
        // a delayed water input instead of treating falling snow as liquid.
        double thawWarmth = unit((safeWeather.temperature() - 0.5) / 8.0);
        double thaw = Math.max(0.0, maximumRainUnits)
                * safeWeather.surface().snowpack()
                * thawWarmth
                * 0.28
                * rainMultiplier;

        double warmth = unit((safeWeather.temperature() + 5.0) / 40.0);
        double vaporDeficit = unit(1.0 - safeWeather.humidity());
        double ventilation = unit(0.35 + safeWeather.wind().magnitude() * 0.65);
        double precipitationSuppression = 1.0 - precipitation * 0.88;
        double frozenSuppression = 1.0 - safeWeather.surface().frozenFraction() * 0.95;
        double evaporation = Math.max(0.0, maximumEvaporationUnits)
                * warmth
                * vaporDeficit
                * ventilation
                * precipitationSuppression
                * frozenSuppression
                * evaporationMultiplier;
        return finite(rain + thaw - evaporation);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, finite(value)));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
