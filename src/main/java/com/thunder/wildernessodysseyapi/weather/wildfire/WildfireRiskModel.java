package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereEnvironment;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherHazardModel;

/**
 * Derives campfire wildfire risk from authoritative local weather and season input.
 *
 * <p>The model is side-effect free. A supported external calendar supplies a
 * narrow temperate-summer or tropical-dry-season window. Without a calendar,
 * ignition remains possible only during a stricter heat-and-drought fallback,
 * so Wilderness does not invent a competing season clock.</p>
 */
public final class WildfireRiskModel {

    private static final double MINIMUM_DROUGHT = 0.80;
    private static final double MAXIMUM_HUMIDITY = 0.28;
    private static final double MINIMUM_TEMPERATURE_CELSIUS = 30.0;
    private static final double MAXIMUM_SURFACE_WETNESS = 0.08;
    private static final double MAXIMUM_SNOWPACK = 0.02;
    private static final double MINIMUM_WIND = 0.15;
    private static final double MINIMUM_FIRE_SEASON = 0.65;
    private static final double FALLBACK_MINIMUM_DROUGHT = 0.92;
    private static final double FALLBACK_MAXIMUM_HUMIDITY = 0.16;
    private static final double FALLBACK_MINIMUM_TEMPERATURE_CELSIUS = 38.0;

    private WildfireRiskModel() {
    }

    /** Evaluates one immutable fire-risk profile for a loaded campfire region. */
    public static RiskProfile evaluate(WeatherSample sample, AtmosphereEnvironment environment) {
        WeatherSample weather = sample == null ? WeatherSample.CLEAR : sample;
        AtmosphereEnvironment inputs = environment == null
                ? AtmosphereEnvironment.TEMPERATE
                : environment;
        double drought = WeatherHazardModel.evaluate(weather).drought();
        double wind = unit(weather.wind().magnitude());
        double airDryness = unit((0.30 - weather.humidity()) / 0.25);
        double heatSupport = unit((weather.temperature() - MINIMUM_TEMPERATURE_CELSIUS) / 18.0);
        double groundDryness = unit(1.0 - weather.surface().wetness() / 0.20);
        boolean calendarAvailable = inputs.seasonCalendarAvailable();
        double fireSeason = calendarAvailable
                ? inputs.fireSeasonFactor()
                : fallbackFireSeason(weather, drought);

        boolean dryWeather = !weather.hasPrecipitation()
                && weather.precipitationIntensity() <= 0.01;
        boolean drySurface = weather.surface().wetness() <= MAXIMUM_SURFACE_WETNESS
                && weather.surface().snowpack() <= MAXIMUM_SNOWPACK;
        boolean seasonEligible = calendarAvailable
                ? fireSeason >= MINIMUM_FIRE_SEASON
                : drought >= FALLBACK_MINIMUM_DROUGHT
                && weather.humidity() <= FALLBACK_MAXIMUM_HUMIDITY
                && weather.temperature() >= FALLBACK_MINIMUM_TEMPERATURE_CELSIUS;
        boolean eligible = seasonEligible
                && dryWeather
                && drySurface
                && drought >= MINIMUM_DROUGHT
                && weather.humidity() <= MAXIMUM_HUMIDITY
                && weather.temperature() >= MINIMUM_TEMPERATURE_CELSIUS
                && wind >= MINIMUM_WIND;

        double risk = unit(
                drought * 0.36
                        + airDryness * 0.16
                        + heatSupport * 0.16
                        + groundDryness * 0.12
                        + unit((wind - MINIMUM_WIND) / 0.65) * 0.10
                        + fireSeason * 0.10
        );
        return new RiskProfile(
                eligible,
                risk,
                drought,
                fireSeason,
                airDryness,
                groundDryness,
                wind,
                calendarAvailable
        );
    }

    private static double fallbackFireSeason(WeatherSample weather, double drought) {
        double exceptionalHeat = unit((weather.temperature() - 34.0) / 16.0);
        double exceptionalDrought = unit((drought - 0.75) / 0.25);
        return exceptionalHeat * exceptionalDrought;
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    /** Immutable diagnostics consumed by the probability policy and operator command. */
    public record RiskProfile(
            boolean eligible,
            double risk,
            double drought,
            double fireSeason,
            double airDryness,
            double groundDryness,
            double wind,
            boolean calendarAvailable
    ) {
        public RiskProfile {
            risk = unit(risk);
            drought = unit(drought);
            fireSeason = unit(fireSeason);
            airDryness = unit(airDryness);
            groundDryness = unit(groundDryness);
            wind = unit(wind);
        }
    }
}
