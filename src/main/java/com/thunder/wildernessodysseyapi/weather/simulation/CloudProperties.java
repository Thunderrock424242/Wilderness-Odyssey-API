package com.thunder.wildernessodysseyapi.weather.simulation;

/** Diagnostic cloud geometry above local terrain; derived from retained mass and layer structure. */
public record CloudProperties(double baseMetres, double topMetres, double liquidFraction,
                               double iceFraction, double precipitationPotential, double convectiveFraction) {
    public double depthMetres() {
        return Math.max(0.0, topMetres - baseMetres);
    }

    public static CloudProperties derive(AtmosphericPhysicalState state) {
        double mass = state.cloudWaterKgPerSquareMetre();
        if (mass < 1.0E-10) {
            return new CloudProperties(0, 0, 0, 0, 0, 0);
        }
        double dewpoint = AtmosphericThermodynamics.dewPointTemperature(state.temperatureCelsius(), state.relativeHumidity());
        double base = AtmosphericUnits.clamp(125.0 * (state.temperatureCelsius() - dewpoint), 0, 5500);
        double convection = AtmosphericUnits.unit(state.instabilityJoulesPerKg() / 2500.0
                * Math.max(0.0, state.verticalVelocityMetresPerSecond()) / 5.0);
        double depth = Math.min(7000.0 - base, 150.0 + mass * 900.0 + convection * 4000.0);
        return new CloudProperties(base, base + depth, state.cloudLiquidKgPerSquareMetre() / mass,
                state.cloudIceKgPerSquareMetre() / mass, AtmosphericUnits.unit((mass - 0.4) / 1.6), convection);
    }
}
