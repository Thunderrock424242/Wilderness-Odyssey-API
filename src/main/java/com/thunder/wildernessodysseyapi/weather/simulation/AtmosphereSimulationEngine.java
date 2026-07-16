package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Pure first-pass atmospheric calculation for one cell.
 *
 * <p>The engine reads only immutable samples and captured environment data. It
 * never touches levels, chunks, registries, or mutable authority state, so a
 * scheduler may calculate cells away from the server thread and reject stale
 * results by cell revision before applying them.</p>
 *
 * <p>One step applies environmental temperature relaxation, temperature-driven
 * pressure, pressure-driven wind, upwind temperature/moisture transport,
 * evaporation, condensation, precipitation loss, and storm-energy growth.</p>
 */
public final class AtmosphereSimulationEngine {
    private static final double TEMPERATURE_RELAXATION = 0.035;
    private static final double THERMAL_PRESSURE_COUPLING = 0.0025;
    private static final double PRESSURE_TO_WIND = 4.0;
    private static final double CLOUD_CONDENSATION_RATE = 0.28;
    private static final double PRECIPITATION_RESPONSE = 0.35;
    private static final double PRECIPITATION_LOSS = 0.04;
    private static final double STORM_GROWTH = 0.12;
    private static final double STORM_DECAY = 0.025;

    /**
     * Advances one cell from an immutable previous-state capture.
     *
     * @param current previous cell-center weather
     * @param environment cached world-derived environmental input
     * @param neighborhood previous-state cardinal neighbors
     * @param settings clamp-safe simulation controls
     * @return a new immutable weather sample
     */
    public WeatherSample simulate(
            WeatherSample current,
            AtmosphereEnvironment environment,
            Neighborhood neighborhood,
            SimulationSettings settings
    ) {
        WeatherSample center = Objects.requireNonNullElse(current, WeatherSample.CLEAR);
        AtmosphereEnvironment inputs = Objects.requireNonNullElse(environment, AtmosphereEnvironment.TEMPERATE);
        SimulationSettings controls = Objects.requireNonNullElse(settings, SimulationSettings.DEFAULT);
        Neighborhood neighbors = neighborhood == null ? Neighborhood.uniform(center) : neighborhood.withFallback(center);
        double step = controls.simulationSpeed();
        if (step == 0.0) {
            return center;
        }

        // Environmental heating/cooling changes pressure relative to adjacent air.
        double targetTemperature = inputs.targetTemperatureCelsius(controls.randomVariation());
        double temperature = approach(center.temperature(), targetTemperature,
                boundedRate(TEMPERATURE_RELAXATION, step));
        double neighborTemperature = neighbors.average(WeatherSample::temperature);
        double neighborPressure = neighbors.average(WeatherSample::pressure);
        double pressureEqualization = boundedRate(controls.pressureEqualizationRate() * 0.25, step);
        double equalizedPressure = approach(center.pressure(), neighborPressure, pressureEqualization);
        double thermalPressureDelta = (neighborTemperature - temperature)
                * THERMAL_PRESSURE_COUPLING
                * controls.pressureEqualizationRate()
                * step;
        double pressure = equalizedPressure + thermalPressureDelta;

        // Pressure gradients accelerate air from higher pressure toward lower pressure.
        double targetWindX = (neighbors.west().pressure() - neighbors.east().pressure()) * PRESSURE_TO_WIND;
        double targetWindZ = (neighbors.north().pressure() - neighbors.south().pressure()) * PRESSURE_TO_WIND;
        double windResponse = boundedRate(0.12 + controls.pressureEqualizationRate() * 0.45, step);
        WindVector wind = new WindVector(
                approach(center.wind().x(), targetWindX, windResponse),
                approach(center.wind().z(), targetWindZ, windResponse)
        );

        // Upwind advection moves temperature, vapor, and existing cloud moisture.
        temperature = transport(
                temperature,
                wind,
                neighbors,
                WeatherSample::temperature,
                controls.temperatureTransportRate(),
                step
        );
        double humidity = transport(
                center.humidity(),
                wind,
                neighbors,
                WeatherSample::humidity,
                controls.humidityTransportRate(),
                step
        );
        double cloudWater = transport(
                center.cloudWater(),
                wind,
                neighbors,
                WeatherSample::cloudWater,
                controls.humidityTransportRate() * 0.8,
                step
        );

        // Humid biomes and cached surface water restore vapor without scanning blocks.
        humidity = approach(humidity, inputs.biomeHumidity(), boundedRate(0.02, step));
        double evaporation = controls.evaporationStrength()
                * inputs.evaporationPotential(temperature, wind.magnitude())
                * (1.0 - unit(humidity))
                * 0.08
                * step;
        humidity = unit(humidity + evaporation);

        // Cooler air saturates sooner; excess vapor condenses into cloud water.
        double saturationThreshold = clamp(
                controls.cloudFormationThreshold() + (temperature - 15.0) * 0.004,
                0.20,
                0.98
        );
        double saturationExcess = Math.max(0.0, humidity - saturationThreshold);
        double condensation = Math.min(
                humidity,
                saturationExcess * CLOUD_CONDENSATION_RATE * (0.75 + center.instability() * 0.25) * step
        );
        humidity = unit(humidity - condensation);
        cloudWater = unit(cloudWater + condensation);
        double cloudDissipation = boundedRate(0.006 + (1.0 - humidity) * 0.008, step);
        cloudWater = approach(cloudWater, 0.0, cloudDissipation);

        // Temperature contrast and moist air build convective instability.
        double temperatureContrast = unit(Math.abs(temperature - neighborTemperature) / 30.0);
        double instabilityTarget = unit(humidity * 0.40 + temperatureContrast * 0.60);
        double instability = approach(center.instability(), instabilityTarget, boundedRate(0.08, step));

        // Moist, unstable, low-pressure cells accumulate persistent storm energy.
        double lowPressureSupport = unit((1.04 - pressure) / 0.20);
        double stormPotential = unit(humidity * instability * (0.55 + lowPressureSupport * 0.45));
        double stormEnergy = center.stormEnergy();
        if (stormPotential > controls.stormFormationThreshold()) {
            stormEnergy += (stormPotential - controls.stormFormationThreshold()) * STORM_GROWTH * step;
        } else {
            stormEnergy -= STORM_DECAY * step;
        }
        stormEnergy = unit(stormEnergy);

        // Cloud water above threshold produces smoothly varying rain or snow.
        double precipitationTarget = 0.0;
        if (cloudWater > controls.precipitationThreshold()) {
            double availableCloud = (cloudWater - controls.precipitationThreshold())
                    / Math.max(0.01, 1.0 - controls.precipitationThreshold());
            precipitationTarget = unit(availableCloud)
                    * controls.maximumPrecipitationIntensity()
                    * (0.75 + stormEnergy * 0.25);
        }
        double precipitationIntensity = approach(
                center.precipitationIntensity(),
                precipitationTarget,
                boundedRate(PRECIPITATION_RESPONSE, step)
        );

        // Falling precipitation depletes condensed water and a smaller vapor share.
        double precipitationLoss = precipitationIntensity
                * PRECIPITATION_LOSS
                * (1.0 + stormEnergy * 0.25)
                * step;
        cloudWater = unit(cloudWater - precipitationLoss);
        humidity = unit(humidity - precipitationLoss * 0.15);
        PrecipitationType precipitationType = precipitationIntensity <= 0.001
                ? PrecipitationType.NONE
                : temperature <= WeatherSample.SNOW_MAX_TEMPERATURE
                        ? PrecipitationType.SNOW
                        : PrecipitationType.RAIN;

        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                wind,
                cloudWater,
                instability,
                stormEnergy,
                precipitationIntensity,
                precipitationType
        );
    }

    /** Alias for schedulers that describe simulation advancement as a step. */
    public WeatherSample step(
            WeatherSample current,
            AtmosphereEnvironment environment,
            Neighborhood neighborhood,
            SimulationSettings settings
    ) {
        return simulate(current, environment, neighborhood, settings);
    }

    private static double transport(
            double current,
            WindVector wind,
            Neighborhood neighbors,
            ToDoubleFunction<WeatherSample> value,
            double configuredRate,
            double step
    ) {
        double xWeight = Math.abs(wind.x());
        double zWeight = Math.abs(wind.z());
        double weight = xWeight + zWeight;
        if (weight <= 1.0e-9 || configuredRate <= 0.0) {
            return current;
        }
        WeatherSample xSource = wind.x() >= 0.0 ? neighbors.west() : neighbors.east();
        WeatherSample zSource = wind.z() >= 0.0 ? neighbors.north() : neighbors.south();
        double source = (value.applyAsDouble(xSource) * xWeight + value.applyAsDouble(zSource) * zWeight) / weight;
        double transportRate = boundedRate(configuredRate * Math.min(1.0, wind.magnitude()), step);
        return approach(current, source, transportRate);
    }

    private static double boundedRate(double rate, double step) {
        return unit(Math.max(0.0, rate) * Math.max(0.0, step));
    }

    private static double approach(double current, double target, double fraction) {
        return current + (target - current) * unit(fraction);
    }

    private static double unit(double value) {
        return clamp(Double.isFinite(value) ? value : 0.0, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Immutable cardinal-neighbor capture from the same grid revision window.
     * Missing edges fall back to the center sample instead of creating weather
     * discontinuities or requiring distant cells to be loaded.
     */
    public record Neighborhood(
            WeatherSample north,
            WeatherSample east,
            WeatherSample south,
            WeatherSample west
    ) {
        /** Creates a no-gradient neighborhood around one sample. */
        public static Neighborhood uniform(WeatherSample sample) {
            WeatherSample value = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
            return new Neighborhood(value, value, value, value);
        }

        private Neighborhood withFallback(WeatherSample fallback) {
            return new Neighborhood(
                    Objects.requireNonNullElse(north, fallback),
                    Objects.requireNonNullElse(east, fallback),
                    Objects.requireNonNullElse(south, fallback),
                    Objects.requireNonNullElse(west, fallback)
            );
        }

        private double average(ToDoubleFunction<WeatherSample> value) {
            return (value.applyAsDouble(north)
                    + value.applyAsDouble(east)
                    + value.applyAsDouble(south)
                    + value.applyAsDouble(west)) * 0.25;
        }
    }
}
