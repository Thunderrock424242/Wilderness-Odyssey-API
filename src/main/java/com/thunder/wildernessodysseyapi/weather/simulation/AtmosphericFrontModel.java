package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphericFrontType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;

import java.util.Objects;

/**
 * Detects bounded weather fronts from cardinal air-mass contrasts.
 *
 * <p>This pure model turns temperature, humidity, pressure, convergence, and
 * wind-shear gradients into lift and gust signals. The simulation consumes the
 * signals gradually, preserving continuous evolution instead of switching
 * between scripted storm presets.</p>
 */
public final class AtmosphericFrontModel {

    private static final double PRESSURE_TO_WIND = 4.0;

    private AtmosphericFrontModel() {
    }

    /** Returns the front crossing the center of an immutable neighborhood. */
    public static FrontState analyze(
            WeatherSample centerSample,
            AtmosphereSimulationEngine.Neighborhood neighborhood
    ) {
        WeatherSample center = Objects.requireNonNullElse(centerSample, WeatherSample.CLEAR);
        AtmosphereSimulationEngine.Neighborhood neighbors = neighborhood == null
                ? AtmosphereSimulationEngine.Neighborhood.uniform(center)
                : neighborhood.withFallback(center);

        WeatherSample[] air = {
                neighbors.north(), neighbors.east(), neighbors.south(), neighbors.west()
        };
        double minimumTemperature = center.temperature();
        double maximumTemperature = center.temperature();
        double minimumHumidity = center.humidity();
        double maximumHumidity = center.humidity();
        double meanTemperature = 0.0;
        double meanHumidity = 0.0;
        double meanPressure = 0.0;
        double windShear = 0.0;
        for (WeatherSample sample : air) {
            minimumTemperature = Math.min(minimumTemperature, sample.temperature());
            maximumTemperature = Math.max(maximumTemperature, sample.temperature());
            minimumHumidity = Math.min(minimumHumidity, sample.humidity());
            maximumHumidity = Math.max(maximumHumidity, sample.humidity());
            meanTemperature += sample.temperature() * 0.25;
            meanHumidity += sample.humidity() * 0.25;
            meanPressure += sample.pressure() * 0.25;
            windShear += Math.hypot(
                    sample.wind().x() - center.wind().x(),
                    sample.wind().z() - center.wind().z()
            ) * 0.25;
        }

        double thermalContrast = unit((maximumTemperature - minimumTemperature) / 18.0);
        double moistureContrast = unit((maximumHumidity - minimumHumidity) / 0.55);
        double pressureTrough = unit((meanPressure - center.pressure()) / 0.12);
        double convergence = clamp(
                (neighbors.west().wind().x() - neighbors.east().wind().x()
                        + neighbors.north().wind().z() - neighbors.south().wind().z()) * 0.5,
                -1.0,
                1.0
        );
        double normalizedShear = unit(windShear / 1.25);
        double strength = unit(
                thermalContrast * 0.42
                        + moistureContrast * 0.16
                        + pressureTrough * 0.22
                        + Math.max(0.0, convergence) * 0.28
                        + normalizedShear * 0.15
        );
        if (strength < 0.12) {
            return FrontState.NONE;
        }

        double temperatureOffset = meanTemperature - center.temperature();
        AtmosphericFrontType type;
        if (pressureTrough >= 0.48 && convergence >= 0.30 && Math.abs(temperatureOffset) < 1.75) {
            type = AtmosphericFrontType.OCCLUDED;
        } else if (temperatureOffset >= 1.5) {
            type = AtmosphericFrontType.WARM;
        } else if (temperatureOffset <= -1.5) {
            type = AtmosphericFrontType.COLD;
        } else {
            type = AtmosphericFrontType.STATIONARY;
        }

        double liftMultiplier = switch (type) {
            case COLD -> 1.0;
            case OCCLUDED -> 0.92;
            case STATIONARY -> 0.72;
            case WARM -> 0.58;
            case NONE -> 0.0;
        };
        double lift = unit(strength
                * (0.30
                + Math.max(0.0, convergence) * 0.42
                + pressureTrough * 0.28
                + meanHumidity * 0.20)
                * liftMultiplier);
        double stormBoost = unit(strength
                * (0.28 + meanHumidity * 0.40 + normalizedShear * 0.24)
                * (type == AtmosphericFrontType.WARM ? 0.62 : 1.0));

        // Gust direction follows the same pressure gradient as the resolved
        // wind, while strength stays bounded so fronts cannot create teleports.
        WindVector gust = new WindVector(
                (neighbors.west().pressure() - neighbors.east().pressure()) * PRESSURE_TO_WIND,
                (neighbors.north().pressure() - neighbors.south().pressure()) * PRESSURE_TO_WIND
        ).limited(1.0);
        gust = new WindVector(gust.x() * strength * 0.35, gust.z() * strength * 0.35);
        return new FrontState(type, strength, lift, stormBoost, gust);
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }

    /** Immutable front signals consumed by one simulation step. */
    public record FrontState(
            AtmosphericFrontType type,
            double strength,
            double lift,
            double stormBoost,
            WindVector gust
    ) {
        public static final FrontState NONE = new FrontState(
                AtmosphericFrontType.NONE, 0.0, 0.0, 0.0, WindVector.ZERO
        );

        public FrontState {
            type = Objects.requireNonNullElse(type, AtmosphericFrontType.NONE);
            strength = unit(strength);
            lift = unit(lift);
            stormBoost = unit(stormBoost);
            gust = Objects.requireNonNullElse(gust, WindVector.ZERO).limited(0.35);
        }
    }
}
