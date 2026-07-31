package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.StormStage;
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
 * pressure, pressure-driven wind, conservative face transport, vapor-capacity
 * condensation, terrain/convergence lift, precipitation loss, vertical cloud
 * development, and storm lifecycle hysteresis.</p>
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

        // Shared-face fluxes move air properties without the one-sided blur
        // produced by selecting only one upwind neighbor.
        temperature = conservativeTransport(
                temperature,
                center,
                neighbors,
                WeatherSample::temperature,
                controls.temperatureTransportRate(),
                step
        );
        double vapor = conservativeTransport(
                AtmosphericThermodynamics.vaporContent(
                        center.temperature(),
                        center.humidity()
                ),
                center,
                neighbors,
                sample -> AtmosphericThermodynamics.vaporContent(
                        sample.temperature(),
                        sample.humidity()
                ),
                controls.humidityTransportRate(),
                step
        );
        double cloudWater = conservativeTransport(
                center.cloudWater(),
                center,
                neighbors,
                WeatherSample::cloudWater,
                controls.humidityTransportRate() * 0.8,
                step
        );

        // Humid biomes and cached surface water restore vapor without scanning blocks.
        double vaporCapacity = AtmosphericThermodynamics.saturationCapacity(temperature);
        double environmentalVapor = inputs.biomeHumidity() * vaporCapacity;
        vapor = approach(vapor, environmentalVapor, boundedRate(0.02, step));
        double humidity = AtmosphericThermodynamics.relativeHumidity(temperature, vapor);
        double evaporation = controls.evaporationStrength()
                * inputs.evaporationPotential(temperature, wind.magnitude())
                * (1.0 - unit(humidity))
                * 0.08
                * step;
        vapor += evaporation * vaporCapacity;
        humidity = AtmosphericThermodynamics.relativeHumidity(temperature, vapor);

        // Temperature-dependent vapor capacity makes cooling air condense even
        // when its absolute moisture inventory has not changed.
        double saturationInventory = vaporCapacity * controls.cloudFormationThreshold();
        double saturationExcess = Math.max(0.0, vapor - saturationInventory);
        double condensation = Math.min(humidity, saturationExcess / vaporCapacity)
                * CLOUD_CONDENSATION_RATE
                * (0.75 + center.instability() * 0.25)
                * step;
        vapor = Math.max(0.0, vapor - condensation * vaporCapacity);
        humidity = AtmosphericThermodynamics.relativeHumidity(temperature, vapor);
        cloudWater = unit(cloudWater + condensation);
        double cloudDissipation = boundedRate(0.006 + (1.0 - humidity) * 0.008, step);
        cloudWater = approach(cloudWater, 0.0, cloudDissipation);

        // Convergence, buoyancy, and windward terrain build vertical motion.
        double temperatureContrast = unit(Math.abs(temperature - neighborTemperature) / 30.0);
        double convergence = clamp(
                (neighbors.west().wind().x() - neighbors.east().wind().x()
                        + neighbors.north().wind().z() - neighbors.south().wind().z()) * 0.5,
                -1.0,
                1.0
        );
        double buoyancy = clamp((temperature - neighborTemperature) / 18.0, -1.0, 1.0);
        double lowPressureSupport = unit((1.04 - pressure) / 0.20);
        double liftTarget = clamp(
                convergence * 0.38
                        + buoyancy * 0.30
                        + inputs.orographicLift(wind) * 0.42
                        + lowPressureSupport * 0.16
                        + inputs.seasonalStorminessOffset() * 0.28
                        - center.precipitationIntensity() * 0.12,
                -1.0,
                1.0
        );
        double verticalMotion = approach(
                center.verticalMotion(),
                liftTarget,
                boundedRate(0.14, step)
        );

        // Moisture, horizontal contrast, and ascent build convective instability.
        double instabilityTarget = unit(
                humidity * 0.34
                        + temperatureContrast * 0.38
                        + Math.max(0.0, verticalMotion) * 0.28
                        + inputs.seasonalStorminessOffset() * 0.20
        );
        double instability = approach(center.instability(), instabilityTarget, boundedRate(0.08, step));

        // Mature storms decay more slowly than forming cells, preventing rapid
        // threshold flicker while precipitation unloads the cloud column.
        double stormPotential = unit(
                humidity
                        * instability
                        * (0.44 + lowPressureSupport * 0.34
                        + Math.max(0.0, verticalMotion) * 0.30)
                        + inputs.seasonalStorminessOffset()
        );
        double stormEnergy = center.stormEnergy();
        if (stormPotential > controls.stormFormationThreshold()) {
            stormEnergy += (stormPotential - controls.stormFormationThreshold()) * STORM_GROWTH * step;
        } else {
            double lifecycleDecay = center.stormStage()
                    == StormStage.MATURE
                    ? STORM_DECAY * 0.45
                    : STORM_DECAY;
            stormEnergy -= lifecycleDecay * step;
        }
        stormEnergy = unit(stormEnergy);

        // Cloud water above threshold produces smoothly varying rain or snow.
        double precipitationTarget = 0.0;
        if (cloudWater > controls.precipitationThreshold()) {
            double availableCloud = (cloudWater - controls.precipitationThreshold())
                    / Math.max(0.01, 1.0 - controls.precipitationThreshold());
            precipitationTarget = unit(availableCloud)
                    * controls.maximumPrecipitationIntensity()
                    * (0.68 + stormEnergy * 0.22 + Math.max(0.0, verticalMotion) * 0.10);
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
        vapor = Math.max(0.0, vapor - precipitationLoss * vaporCapacity * 0.15);
        humidity = AtmosphericThermodynamics.relativeHumidity(temperature, vapor);
        double wetBulbTemperature = AtmosphericThermodynamics.wetBulbTemperature(
                temperature,
                humidity
        );
        PrecipitationType precipitationType = precipitationIntensity <= 0.001
                ? PrecipitationType.NONE
                : wetBulbTemperature <= WeatherSample.SNOW_MAX_TEMPERATURE
                        ? PrecipitationType.SNOW
                        : PrecipitationType.RAIN;

        double cloudDepthTarget = unit(
                cloudWater * 0.38
                        + instability * 0.24
                        + stormEnergy * 0.24
                        + Math.max(0.0, verticalMotion) * 0.30
        );
        double cloudDepth = approach(center.cloudDepth(), cloudDepthTarget, boundedRate(0.12, step));
        WindVector cloudWindTarget = new WindVector(
                wind.x() - verticalMotion * wind.z() * 0.22,
                wind.z() + verticalMotion * wind.x() * 0.22
        );
        WindVector cloudWind = WindVector.lerp(
                center.cloudWind(),
                cloudWindTarget,
                boundedRate(0.10, step)
        );

        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                wind,
                cloudWater,
                instability,
                stormEnergy,
                precipitationIntensity,
                precipitationType,
                verticalMotion,
                cloudDepth,
                cloudWind
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

    private static double conservativeTransport(
            double current,
            WeatherSample center,
            Neighborhood neighbors,
            ToDoubleFunction<WeatherSample> value,
            double configuredRate,
            double step
    ) {
        if (configuredRate <= 0.0) {
            return current;
        }

        double eastFlux = faceFlux(
                current,
                value.applyAsDouble(neighbors.east()),
                center,
                neighbors.east(),
                true
        );
        double westFlux = faceFlux(
                value.applyAsDouble(neighbors.west()),
                current,
                neighbors.west(),
                center,
                true
        );
        double southFlux = faceFlux(
                current,
                value.applyAsDouble(neighbors.south()),
                center,
                neighbors.south(),
                false
        );
        double northFlux = faceFlux(
                value.applyAsDouble(neighbors.north()),
                current,
                neighbors.north(),
                center,
                false
        );
        double rate = boundedRate(configuredRate * 0.50, step);
        return current + (westFlux - eastFlux + northFlux - southFlux) * rate;
    }

    private static double faceFlux(
            double negativeSideValue,
            double positiveSideValue,
            WeatherSample negativeSide,
            WeatherSample positiveSide,
            boolean xAxis
    ) {
        double negativeWind = xAxis ? negativeSide.wind().x() : negativeSide.wind().z();
        double positiveWind = xAxis ? positiveSide.wind().x() : positiveSide.wind().z();
        double pressureVelocity = (negativeSide.pressure() - positiveSide.pressure())
                * PRESSURE_TO_WIND;
        double velocity = clamp(
                pressureVelocity + (negativeWind + positiveWind) * 0.5,
                -1.0,
                1.0
        );
        return velocity * (velocity >= 0.0 ? negativeSideValue : positiveSideValue);
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
