package com.thunder.wildernessodysseyapi.weather.simulation;

/**
 * Immutable, clamp-safe controls used by the pure atmospheric engine.
 *
 * <p>Scheduling, persistence, and networking controls remain outside this
 * calculation contract. Every rate is a fraction of one nominal simulation
 * update and is multiplied by {@link #simulationSpeed()}.</p>
 */
public record SimulationSettings(
        double simulationSpeed,
        double humidityTransportRate,
        double temperatureTransportRate,
        double pressureEqualizationRate,
        double evaporationStrength,
        double cloudFormationThreshold,
        double precipitationThreshold,
        double stormFormationThreshold,
        double maximumPrecipitationIntensity,
        double randomVariation
) {
    public static final SimulationSettings DEFAULT = new SimulationSettings(
            1.0,
            0.18,
            0.10,
            0.20,
            0.12,
            0.72,
            0.58,
            0.42,
            1.0,
            0.04
    );

    public SimulationSettings {
        simulationSpeed = clamp(finiteOr(simulationSpeed, 1.0), 0.0, 8.0);
        humidityTransportRate = unit(humidityTransportRate);
        temperatureTransportRate = unit(temperatureTransportRate);
        pressureEqualizationRate = unit(pressureEqualizationRate);
        evaporationStrength = unit(evaporationStrength);
        cloudFormationThreshold = clamp(finiteOr(cloudFormationThreshold, 0.72), 0.05, 0.99);
        precipitationThreshold = clamp(finiteOr(precipitationThreshold, 0.58), 0.05, 0.99);
        stormFormationThreshold = unit(stormFormationThreshold);
        maximumPrecipitationIntensity = unit(maximumPrecipitationIntensity);
        randomVariation = clamp(finiteOr(randomVariation, 0.04), 0.0, 0.25);
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
