package com.thunder.wildernessodysseyapi.weather.simulation;

/** Low-cost surface heat store and air exchange; no per-block heat simulation. */
public final class SurfaceEnergyModel {
    private SurfaceEnergyModel() { }

    /** Advances skin/mixed-layer temperature using W/m2 and effective J/(m2 K). */
    public static double surfaceTemperature(AtmosphericPhysicalState state, AtmosphereEnvironment environment,
                                            double seconds, double variation) {
        double water = environment.waterCoverage();
        double vegetation = environment.biomeHumidity() * (1.0 - water);
        double snow = state.surface().snowpack();
        double cloud = AtmosphericUnits.unit(state.cloudWaterKgPerSquareMetre() / 2.0);
        double solar = Math.max(0.0, environment.daylight() * 2.0 - 1.0);
        double seasonSolar = Double.isFinite(environment.seasonalCyclePhase())
                ? 0.85 + 0.15 * Math.cos((environment.seasonalCyclePhase() - 0.375) * Math.PI * 2) : 1.0;
        double albedo = 0.18 + vegetation * 0.05 - water * 0.10 + snow * 0.55;
        double shortwave = 700.0 * solar * seasonSolar * (1.0 - albedo) * (1.0 - cloud * 0.75);
        double longwave = (35.0 + (1.0 - cloud) * 70.0) * (1.0 - solar * 0.5);
        // Water's half-metre mixed layer stores far more heat than the responsive land skin.
        double heatCapacity = 60_000.0 + vegetation * 80_000.0 + state.surface().wetness() * 80_000.0
                + water * 2_100_000.0;
        double background = environment.targetTemperatureCelsius(variation)
                - (environment.daylight() - 0.5) * 8.0;
        double tau = 1800.0 + water * 14_400.0;
        double equilibrium = background + (shortwave - longwave) * tau / heatCapacity;
        return state.surfaceTemperatureCelsius() + (equilibrium - state.surfaceTemperatureCelsius())
                * AtmosphericUnits.response(seconds, tau);
    }

    /** Boundary-layer air exchanges with the retained surface, with slower ocean response. */
    public static double airTemperature(double air, double surface, double waterCoverage, double seconds) {
        return air + (surface - air) * AtmosphericUnits.response(seconds, 300.0 + waterCoverage * 600.0);
    }
}
