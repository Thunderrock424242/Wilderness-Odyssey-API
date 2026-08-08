package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;

/**
 * Immutable world-derived input captured before atmospheric calculation.
 *
 * <p>Samplers may read biomes, cached terrain metadata, daylight, dimensions,
 * seasons, and compact water coverage on the server thread, then pass this
 * record to pure simulation code without retaining any live world objects.</p>
 *
 * @param biomeTemperatureCelsius biome baseline air temperature
 * @param biomeHumidity biome downfall/humidity in normalized units
 * @param elevationBlocks representative terrain elevation in blocks
 * @param waterCoverage sampled surface-water coverage in normalized units
 * @param daylight local daylight strength in normalized units
 * @param dimensionTemperatureOffset dimension-specific temperature adjustment
 * @param seasonalTemperatureOffset optional season integration adjustment
 * @param atmosphericVariation deterministic local variation in {@code [-1, 1]}
 * @param seasonalStorminessOffset seasonal convective adjustment in {@code [-1, 1]}
 * @param seasonalEvaporationMultiplier seasonal evaporation multiplier
 * @param terrainGradientX average terrain rise toward positive X
 * @param terrainGradientZ average terrain rise toward positive Z
 * @param terrainRoughness normalized local relief
 * @param oceanCoverage exposed ocean-water coverage
 * @param inlandWaterCoverage exposed lake and inland-water coverage
 * @param fireSeasonFactor normalized temperate-summer or tropical-dry-season strength
 * @param snowSeasonFactor normalized temperate-winter snowfall eligibility
 * @param seasonCalendarAvailable whether an external calendar supplied a season phase
 */
public record AtmosphereEnvironment(
        double biomeTemperatureCelsius,
        double biomeHumidity,
        double elevationBlocks,
        double waterCoverage,
        double daylight,
        double dimensionTemperatureOffset,
        double seasonalTemperatureOffset,
        double atmosphericVariation,
        double seasonalStorminessOffset,
        double seasonalEvaporationMultiplier,
        double terrainGradientX,
        double terrainGradientZ,
        double terrainRoughness,
        double oceanCoverage,
        double inlandWaterCoverage,
        double fireSeasonFactor,
        double snowSeasonFactor,
        boolean seasonCalendarAvailable
) {
    public static final AtmosphereEnvironment TEMPERATE = new AtmosphereEnvironment(
            15.0,
            0.45,
            64.0,
            0.0,
            0.5,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            false
    );

    /** Retains the pre-snow-season construction shape for integrations and tests. */
    public AtmosphereEnvironment(
            double biomeTemperatureCelsius,
            double biomeHumidity,
            double elevationBlocks,
            double waterCoverage,
            double daylight,
            double dimensionTemperatureOffset,
            double seasonalTemperatureOffset,
            double atmosphericVariation,
            double seasonalStorminessOffset,
            double seasonalEvaporationMultiplier,
            double terrainGradientX,
            double terrainGradientZ,
            double terrainRoughness,
            double oceanCoverage,
            double inlandWaterCoverage,
            double fireSeasonFactor,
            boolean seasonCalendarAvailable
    ) {
        this(
                biomeTemperatureCelsius,
                biomeHumidity,
                elevationBlocks,
                waterCoverage,
                daylight,
                dimensionTemperatureOffset,
                seasonalTemperatureOffset,
                atmosphericVariation,
                seasonalStorminessOffset,
                seasonalEvaporationMultiplier,
                terrainGradientX,
                terrainGradientZ,
                terrainRoughness,
                oceanCoverage,
                inlandWaterCoverage,
                fireSeasonFactor,
                0.0,
                seasonCalendarAvailable
        );
    }

    /** Retains the weather-v3 construction shape without wildfire-season metadata. */
    public AtmosphereEnvironment(
            double biomeTemperatureCelsius,
            double biomeHumidity,
            double elevationBlocks,
            double waterCoverage,
            double daylight,
            double dimensionTemperatureOffset,
            double seasonalTemperatureOffset,
            double atmosphericVariation,
            double seasonalStorminessOffset,
            double seasonalEvaporationMultiplier,
            double terrainGradientX,
            double terrainGradientZ,
            double terrainRoughness,
            double oceanCoverage,
            double inlandWaterCoverage
    ) {
        this(
                biomeTemperatureCelsius,
                biomeHumidity,
                elevationBlocks,
                waterCoverage,
                daylight,
                dimensionTemperatureOffset,
                seasonalTemperatureOffset,
                atmosphericVariation,
                seasonalStorminessOffset,
                seasonalEvaporationMultiplier,
                terrainGradientX,
                terrainGradientZ,
                terrainRoughness,
                oceanCoverage,
                inlandWaterCoverage,
                0.0,
                0.0,
                false
        );
    }

    /** Retains the weather-v2 construction shape without typed water coverage. */
    public AtmosphereEnvironment(
            double biomeTemperatureCelsius,
            double biomeHumidity,
            double elevationBlocks,
            double waterCoverage,
            double daylight,
            double dimensionTemperatureOffset,
            double seasonalTemperatureOffset,
            double atmosphericVariation,
            double seasonalStorminessOffset,
            double seasonalEvaporationMultiplier,
            double terrainGradientX,
            double terrainGradientZ,
            double terrainRoughness
    ) {
        this(
                biomeTemperatureCelsius,
                biomeHumidity,
                elevationBlocks,
                waterCoverage,
                daylight,
                dimensionTemperatureOffset,
                seasonalTemperatureOffset,
                atmosphericVariation,
                seasonalStorminessOffset,
                seasonalEvaporationMultiplier,
                terrainGradientX,
                terrainGradientZ,
                terrainRoughness,
                0.0,
                0.0,
                0.0,
                0.0,
                false
        );
    }

    /** Retains the original environment constructor for API and test callers. */
    public AtmosphereEnvironment(
            double biomeTemperatureCelsius,
            double biomeHumidity,
            double elevationBlocks,
            double waterCoverage,
            double daylight,
            double dimensionTemperatureOffset,
            double seasonalTemperatureOffset,
            double atmosphericVariation
    ) {
        this(
                biomeTemperatureCelsius,
                biomeHumidity,
                elevationBlocks,
                waterCoverage,
                daylight,
                dimensionTemperatureOffset,
                seasonalTemperatureOffset,
                atmosphericVariation,
                0.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                false
        );
    }

    public AtmosphereEnvironment {
        biomeTemperatureCelsius = clamp(finiteOr(biomeTemperatureCelsius, 15.0), -80.0, 60.0);
        biomeHumidity = unit(biomeHumidity);
        elevationBlocks = clamp(finiteOr(elevationBlocks, 64.0), -128.0, 2048.0);
        waterCoverage = unit(waterCoverage);
        daylight = unit(daylight);
        dimensionTemperatureOffset = clamp(finiteOr(dimensionTemperatureOffset, 0.0), -50.0, 50.0);
        seasonalTemperatureOffset = clamp(finiteOr(seasonalTemperatureOffset, 0.0), -30.0, 30.0);
        atmosphericVariation = clamp(finiteOr(atmosphericVariation, 0.0), -1.0, 1.0);
        seasonalStorminessOffset = clamp(finiteOr(seasonalStorminessOffset, 0.0), -1.0, 1.0);
        seasonalEvaporationMultiplier = clamp(
                finiteOr(seasonalEvaporationMultiplier, 1.0),
                0.25,
                2.0
        );
        terrainGradientX = clamp(finiteOr(terrainGradientX, 0.0), -1.0, 1.0);
        terrainGradientZ = clamp(finiteOr(terrainGradientZ, 0.0), -1.0, 1.0);
        terrainRoughness = unit(terrainRoughness);
        oceanCoverage = unit(oceanCoverage);
        inlandWaterCoverage = unit(inlandWaterCoverage);
        fireSeasonFactor = unit(fireSeasonFactor);
        snowSeasonFactor = unit(snowSeasonFactor);
    }

    /**
     * Computes the environmental temperature target before neighboring-air
     * transport. Elevation cools air by 0.65 C per 100 blocks above sea level.
     */
    public double targetTemperatureCelsius(double configuredVariation) {
        double diurnalOffset = (daylight - 0.5) * 8.0;
        double elevationOffset = -(elevationBlocks - 64.0) * 0.0065;
        double variationOffset = atmosphericVariation * Math.max(0.0, configuredVariation) * 20.0;
        return biomeTemperatureCelsius
                + dimensionTemperatureOffset
                + seasonalTemperatureOffset
                + diurnalOffset
                + elevationOffset
                + variationOffset;
    }

    /** Returns compact water/biome support for evaporation without block scans. */
    public double evaporationPotential(double temperature, double windMagnitude) {
        double warmth = clamp((temperature + 10.0) / 45.0, 0.05, 1.0);
        double ventilation = clamp(0.6 + Math.max(0.0, windMagnitude) * 0.4, 0.6, 1.2);
        return unit((waterCoverage * 0.85 + biomeHumidity * 0.15)
                * warmth
                * ventilation
                * seasonalEvaporationMultiplier);
    }

    /**
     * Returns windward terrain lift without reading terrain during simulation.
     *
     * <p>The cached gradient is a rise-over-run value. Only uphill flow
     * contributes; leeward flow instead receives no artificial uplift.</p>
     */
    public double orographicLift(WindVector wind) {
        if (wind == null) {
            return 0.0;
        }
        double uphill = wind.x() * terrainGradientX + wind.z() * terrainGradientZ;
        return unit(Math.max(0.0, uphill) * 4.0 + terrainRoughness * wind.magnitude() * 0.12);
    }

    /** Returns warm-ocean support for organized maritime storms. */
    public double oceanStormPotential(double airTemperature, double humidity) {
        double warmth = unit((airTemperature - 17.0) / 18.0);
        return unit(oceanCoverage * warmth * (0.35 + unit(humidity) * 0.65));
    }

    /** Returns cold-air-over-water support for lake-effect snow bands. */
    public double lakeEffectPotential(double airTemperature, double windMagnitude) {
        double coldAir = unit((5.0 - airTemperature) / 16.0);
        double ventilation = unit(0.25 + Math.max(0.0, windMagnitude) * 0.75);
        return unit(inlandWaterCoverage * coldAir * ventilation * 2.2);
    }

    private static double unit(double value) {
        return clamp(finiteOr(value, 0.0), 0.0, 1.0);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
