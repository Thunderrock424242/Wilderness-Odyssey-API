package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import java.util.Objects;

/**
 * Pure physical evolution of the existing atmosphere owner.
 * The authority supplies synchronous immutable neighbor generations and elapsed seconds.
 * World sampling, revision checks, water receipt acknowledgement and persistence stay on the server.
 */
public final class AtmosphereSimulationEngine {
    /**
     * Compatibility adapter for callers holding only normalized samples.
     * Persistent owners must retain StepResult.state() rather than reconstructing water from humidity.
     */
    public WeatherSample simulate(WeatherSample current, AtmosphereEnvironment environment,
            Neighborhood neighborhood, SimulationSettings settings) {
        return simulate(current, environment, neighborhood, settings, AtmosphericWaterExchange.Receipt.EMPTY);
    }

    /** Retains the original receipt-taking entry point at the fixed two-second reference interval. */
    public WeatherSample simulate(WeatherSample current, AtmosphereEnvironment environment,
            Neighborhood neighborhood, SimulationSettings settings, AtmosphericWaterExchange.Receipt receipt) {
        WeatherSample sample = Objects.requireNonNullElse(current, WeatherSample.CLEAR);
        SimulationSettings controls = Objects.requireNonNullElse(settings, SimulationSettings.DEFAULT);
        if (controls.simulationSpeed() == 0) {
            return sample;
        }
        AtmosphericPhysicalState state = AtmosphericPhysicalState.fromLegacy(sample);
        Neighborhood values = neighborhood == null ? Neighborhood.uniform(sample) : neighborhood.withFallback(sample);
        PhysicalNeighborhood neighbors = new PhysicalNeighborhood(
                AtmosphericPhysicalState.fromLegacy(values.north()), AtmosphericPhysicalState.fromLegacy(values.east()),
                AtmosphericPhysicalState.fromLegacy(values.south()), AtmosphericPhysicalState.fromLegacy(values.west()));
        double remaining = AtmosphericUnits.REFERENCE_STEP_SECONDS;
        boolean first = true;
        while (remaining > 1.0E-9) {
            double seconds = Math.min(remaining, maximumStepSeconds(AtmosphericUnits.REFERENCE_CELL_METRES, controls));
            // A uniform synthetic neighborhood follows the evolving center. Real grids use the physical API.
            PhysicalNeighborhood inputs = values.north().equals(sample) && values.east().equals(sample)
                    && values.south().equals(sample) && values.west().equals(sample)
                    ? PhysicalNeighborhood.uniform(state) : neighbors;
            StepResult result = simulatePhysical(state, environment, inputs, controls, seconds,
                    AtmosphericUnits.REFERENCE_CELL_METRES, first ? receipt : receipt.withoutFlux());
            state = result.state();
            current = result.sample();
            first = false;
            remaining -= seconds;
        }
        return current;
    }

    /** Alias preserved for integrations. */
    public WeatherSample step(WeatherSample current, AtmosphereEnvironment environment,
            Neighborhood neighborhood, SimulationSettings settings) {
        return simulate(current, environment, neighborhood, settings);
    }

    /** Four-face Courant bound including simulation speed; scheduling may subdivide but never discard time. */
    public static double maximumStepSeconds(double cellSizeMeters, SimulationSettings settings) {
        return Math.min(2.0, Math.max(16.0, cellSizeMeters)
                / (4.0 * AtmosphericUnits.MAX_WIND_METRES_PER_SECOND))
                / Math.max(1.0, settings.simulationSpeed());
    }

    /**
     * Advances one stable explicit step. Receipt evaporation has already been debited at the surface.
     * Boundary exchange on unmodeled land/ocean is separately reported, never disguised as conserved ET.
     */
    public StepResult simulatePhysical(AtmosphericPhysicalState current, AtmosphereEnvironment environment,
            PhysicalNeighborhood neighborhood, SimulationSettings settings, double dtSeconds,
            double cellSizeMeters, AtmosphericWaterExchange.Receipt waterReceipt) {
        Objects.requireNonNull(current, "physical state");
        AtmosphereEnvironment inputs = Objects.requireNonNullElse(environment, AtmosphereEnvironment.TEMPERATE);
        SimulationSettings controls = Objects.requireNonNullElse(settings, SimulationSettings.DEFAULT);
        AtmosphericWaterExchange.Receipt receipt = Objects.requireNonNullElse(waterReceipt, AtmosphericWaterExchange.Receipt.EMPTY);
        PhysicalNeighborhood neighbors = neighborhood == null ? PhysicalNeighborhood.uniform(current) : neighborhood.withFallback(current);
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0 || !Double.isFinite(cellSizeMeters) || cellSizeMeters < 16) {
            throw new IllegalArgumentException("Atmosphere requires nonnegative seconds and cell width >= 16 metres");
        }
        if (dtSeconds > maximumStepSeconds(cellSizeMeters, controls) + 1.0E-8) {
            throw new IllegalArgumentException("Subdivide the entire grid generation to satisfy atmospheric CFL");
        }
        double seconds = dtSeconds * controls.simulationSpeed();
        if (seconds == 0) {
            PrecipitationType phase = phase(current);
            return new StepResult(current, current.toWeatherSample(current.surface(), phase), AtmosphericWaterFlux.NONE);
        }

        double surfaceTemperature = SurfaceEnergyModel.surfaceTemperature(current, inputs, seconds, controls.randomVariation());
        double temperature = SurfaceEnergyModel.airTemperature(current.temperatureCelsius(), surfaceTemperature,
                inputs.waterCoverage(), seconds);
        temperature += AtmosphericTransport.intensiveDelta(current, neighbors, AtmosphericPhysicalState::temperatureCelsius,
                0, seconds, cellSizeMeters, controls.temperatureTransportRate() / 0.10);
        double meanTemperature = (neighbors.north().temperatureCelsius() + neighbors.east().temperatureCelsius()
                + neighbors.south().temperatureCelsius() + neighbors.west().temperatureCelsius()) * 0.25;
        double meanPressure = (neighbors.north().pressureHpa() + neighbors.east().pressureHpa()
                + neighbors.south().pressureHpa() + neighbors.west().pressureHpa()) * 0.25;
        double pressure = current.pressureHpa() + (meanPressure - current.pressureHpa())
                * AtmosphericUnits.response(seconds, Math.max(20.0, cellSizeMeters * cellSizeMeters / 150.0))
                * controls.pressureEqualizationRate();
        pressure += (meanTemperature - temperature) * 0.004 * seconds * controls.pressureEqualizationRate();

        double vaporDelta = AtmosphericTransport.delta(current, neighbors, AtmosphericPhysicalState::vaporKgPerSquareMetre,
                0, seconds, cellSizeMeters, controls.humidityTransportRate() / 0.18);
        double liquidDelta = AtmosphericTransport.delta(current, neighbors, AtmosphericPhysicalState::cloudLiquidKgPerSquareMetre,
                2, seconds, cellSizeMeters, controls.humidityTransportRate() / 0.18);
        double iceDelta = AtmosphericTransport.delta(current, neighbors, AtmosphericPhysicalState::cloudIceKgPerSquareMetre,
                3, seconds, cellSizeMeters, controls.humidityTransportRate() / 0.18);
        double vapor = nonnegativeRoundoff(current.vaporKgPerSquareMetre() + vaporDelta);
        double liquid = nonnegativeRoundoff(current.cloudLiquidKgPerSquareMetre() + liquidDelta);
        double ice = nonnegativeRoundoff(current.cloudIceKgPerSquareMetre() + iceDelta);

        double humidity = AtmosphericUnits.unit(vapor / AtmosphericThermodynamics.saturationColumnWater(temperature));
        double evaporation = receipt.evaporatedVaporInventory() * AtmosphericUnits.VAPOR_SCALE_KG_PER_SQUARE_METRE;
        double boundary = (inputs.biomeHumidity() * AtmosphericThermodynamics.saturationColumnWater(temperature) - vapor)
                * AtmosphericUnits.response(seconds, 7200.0) * (1.0 - receipt.coveredFraction());
        // Bulk aerodynamic lake/ocean/soil boundary: stronger over warm water in dry, ventilated air.
        double saturationDeficit = Math.max(0.0, AtmosphericThermodynamics.saturationColumnWater(surfaceTemperature) - vapor);
        boundary += controls.evaporationStrength() * inputs.evaporationPotential(surfaceTemperature,
                current.column().surface().windSpeedMetresPerSecond() / 20.0)
                * saturationDeficit * (0.5 + current.column().surface().windSpeedMetresPerSecond() / 10.0)
                * seconds / 1800.0 * (1.0 - receipt.coveredFraction());
        vapor += evaporation + boundary;

        AtmosphericColumn column = AtmosphericColumnModel.advance(current, neighbors, inputs, temperature,
                pressure, humidity, seconds, cellSizeMeters, controls);
        AtmosphericFrontModel.FrontState front = AtmosphericFrontModel.analyze(current, neighbors, cellSizeMeters);
        double convergence = (neighbors.west().column().surface().windXMetresPerSecond()
                - neighbors.east().column().surface().windXMetresPerSecond()
                + neighbors.north().column().surface().windZMetresPerSecond()
                - neighbors.south().column().surface().windZMetresPerSecond()) / (2.0 * cellSizeMeters);
        double terrainLift = WindPhysicsModel.orographicVelocity(inputs, column.surface());
        double cape = column.instabilityJoulesPerKg();
        double buoyantLift = Math.sqrt(2.0 * cape) * 0.06 * humidity;
        double liftTarget = AtmosphericUnits.clamp(convergence * 300.0 + terrainLift
                + buoyantLift + front.lift() * controls.weatherFrontStrength() * 8.0
                - current.precipitationMmPerHour() * 0.025, -20.0, 40.0);
        double lift = current.verticalVelocityMetresPerSecond() + (liftTarget - current.verticalVelocityMetresPerSecond())
                * AtmosphericUnits.response(seconds, 30.0);
        // Adiabatic cooling on windward slopes, warming and RH loss in descending air.
        temperature -= lift * 0.0065 * seconds;
        double instability = current.instabilityJoulesPerKg() + (cape - current.instabilityJoulesPerKg())
                * AtmosphericUnits.response(seconds, 90.0);
        double potential = AtmosphericUnits.unit(humidity * (instability / 2200.0)
                * (0.35 + Math.max(0.0, lift) / 10.0) * (0.7 + column.shearMetresPerSecond() / 50.0)
                + front.stormBoost() * controls.weatherFrontStrength() * 0.2
                + inputs.oceanStormPotential(temperature, humidity) * Math.max(0.0, lift) / 40.0);
        double storm = current.stormEnergy();
        if (potential > controls.stormFormationThreshold()) {
            storm += (potential - storm) * AtmosphericUnits.response(seconds, 180.0);
        } else {
            storm *= Math.exp(-seconds / (storm >= 0.42 ? 600.0 : 240.0));
        }

        AtmosphericMoistureBudget.Result moisture = AtmosphericMoistureBudget.advance(vapor, liquid, ice, temperature,
                column.middle().temperatureCelsius(), lift, instability, seconds, controls);
        temperature += moisture.latentTemperatureChange();
        humidity = AtmosphericUnits.unit(moisture.vapor() / AtmosphericThermodynamics.saturationColumnWater(temperature));
        AtmosphericLayer air = column.surface();
        column = new AtmosphericColumn(new AtmosphericLayer(0, pressure, temperature, humidity,
                air.windXMetresPerSecond(), air.windZMetresPerSecond()), column.low(), column.middle(), column.upper());
        AtmosphericPhysicalState next = new AtmosphericPhysicalState(temperature, pressure, moisture.vapor(),
                moisture.liquid(), moisture.ice(), column, lift, instability, storm, surfaceTemperature,
                moisture.precipitation() * 3600.0 / dtSeconds, current.surface());
        PrecipitationType type = phase(next);
        WeatherSample atmosphere = next.toWeatherSample(current.surface(), type);
        SurfaceWeatherState surface = SurfaceWeatherModel.simulate(current.surface(), atmosphere, inputs,
                seconds / AtmosphericUnits.REFERENCE_STEP_SECONDS, receipt);
        next = new AtmosphericPhysicalState(next.temperatureCelsius(), next.pressureHpa(), next.vaporKgPerSquareMetre(),
                next.cloudLiquidKgPerSquareMetre(), next.cloudIceKgPerSquareMetre(), next.column(),
                next.verticalVelocityMetresPerSecond(), next.instabilityJoulesPerKg(), next.stormEnergy(),
                next.surfaceTemperatureCelsius(), next.precipitationMmPerHour(), surface);
        double precipitation = moisture.precipitation();
        AtmosphericWaterFlux flux = new AtmosphericWaterFlux(dtSeconds, evaporation, moisture.condensation(),
                moisture.cloudEvaporation(), type == PrecipitationType.RAIN ? precipitation : 0,
                type == PrecipitationType.SNOW ? precipitation : 0, type == PrecipitationType.SLEET ? precipitation : 0,
                type == PrecipitationType.FREEZING_RAIN ? precipitation : 0, type == PrecipitationType.HAIL ? precipitation : 0,
                boundary, vaporDelta + liquidDelta + iceDelta);
        return new StepResult(next, next.toWeatherSample(surface, type), flux);
    }

    private static PrecipitationType phase(AtmosphericPhysicalState state) {
        return PrecipitationPhaseModel.classify(state.precipitationMmPerHour(), state.column(),
                state.instabilityJoulesPerKg(), state.verticalVelocityMetresPerSecond(),
                CloudProperties.derive(state).depthMetres());
    }

    private static double nonnegativeRoundoff(double water) {
        if (water < -1.0E-9) {
            throw new IllegalStateException("Negative atmospheric inventory: grid timestep violates conservation bound");
        }
        return Math.max(0.0, water);
    }

    /** All physical state and integrated water transfers from one accepted numerical step. */
    public record StepResult(AtmosphericPhysicalState state, WeatherSample sample, AtmosphericWaterFlux flux) { }

    /** Physical neighbors captured from one immutable generation, never partially updated live cells. */
    public record PhysicalNeighborhood(AtmosphericPhysicalState north, AtmosphericPhysicalState east,
            AtmosphericPhysicalState south, AtmosphericPhysicalState west, int closedFaces) {
        public PhysicalNeighborhood(AtmosphericPhysicalState north, AtmosphericPhysicalState east,
                AtmosphericPhysicalState south, AtmosphericPhysicalState west) {
            this(north, east, south, west, 0);
        }

        /** Unresolved or differently timed faces are closed, rather than exporting untracked mass. */
        public static PhysicalNeighborhood bounded(AtmosphericPhysicalState center, AtmosphericPhysicalState north,
                AtmosphericPhysicalState east, AtmosphericPhysicalState south, AtmosphericPhysicalState west) {
            int mask = (north == null ? 1 : 0) | (east == null ? 2 : 0) | (south == null ? 4 : 0) | (west == null ? 8 : 0);
            return new PhysicalNeighborhood(Objects.requireNonNullElse(north, center), Objects.requireNonNullElse(east, center),
                    Objects.requireNonNullElse(south, center), Objects.requireNonNullElse(west, center), mask);
        }
        public static PhysicalNeighborhood uniform(AtmosphericPhysicalState state) {
            return new PhysicalNeighborhood(state, state, state, state);
        }

        PhysicalNeighborhood withFallback(AtmosphericPhysicalState state) {
            return new PhysicalNeighborhood(Objects.requireNonNullElse(north, state), Objects.requireNonNullElse(east, state),
                    Objects.requireNonNullElse(south, state), Objects.requireNonNullElse(west, state), closedFaces);
        }
    }

    /** Original normalized neighbor API retained for front, forecast and third-party callers. */
    public record Neighborhood(WeatherSample north, WeatherSample east, WeatherSample south, WeatherSample west) {
        public static Neighborhood uniform(WeatherSample sample) {
            WeatherSample value = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
            return new Neighborhood(value, value, value, value);
        }

        Neighborhood withFallback(WeatherSample fallback) {
            return new Neighborhood(Objects.requireNonNullElse(north, fallback), Objects.requireNonNullElse(east, fallback),
                    Objects.requireNonNullElse(south, fallback), Objects.requireNonNullElse(west, fallback));
        }
    }
}
