package com.thunder.wildernessodysseyapi.weather.simulation;

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
 */
public record AtmosphereEnvironment(
        double biomeTemperatureCelsius,
        double biomeHumidity,
        double elevationBlocks,
        double waterCoverage,
        double daylight,
        double dimensionTemperatureOffset,
        double seasonalTemperatureOffset,
        double atmosphericVariation
) {
    public static final AtmosphereEnvironment TEMPERATE = new AtmosphereEnvironment(
            15.0,
            0.45,
            64.0,
            0.0,
            0.5,
            0.0,
            0.0,
            0.0
    );

    public AtmosphereEnvironment {
        biomeTemperatureCelsius = clamp(finiteOr(biomeTemperatureCelsius, 15.0), -80.0, 60.0);
        biomeHumidity = unit(biomeHumidity);
        elevationBlocks = clamp(finiteOr(elevationBlocks, 64.0), -128.0, 2048.0);
        waterCoverage = unit(waterCoverage);
        daylight = unit(daylight);
        dimensionTemperatureOffset = clamp(finiteOr(dimensionTemperatureOffset, 0.0), -50.0, 50.0);
        seasonalTemperatureOffset = clamp(finiteOr(seasonalTemperatureOffset, 0.0), -30.0, 30.0);
        atmosphericVariation = clamp(finiteOr(atmosphericVariation, 0.0), -1.0, 1.0);
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
        return unit((waterCoverage * 0.85 + biomeHumidity * 0.15) * warmth * ventilation);
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
