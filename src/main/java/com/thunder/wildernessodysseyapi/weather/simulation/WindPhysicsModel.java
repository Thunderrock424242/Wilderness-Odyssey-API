package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;

/** Pressure acceleration, terrain drag and configurable regional Coriolis, all in SI units. */
public final class WindPhysicsModel {
    private WindPhysicsModel() { }

    /** Evolves a layer's velocity; pressure inputs are hPa at a common reference elevation. */
    public static WindVector advance(AtmosphericLayer layer, double westPressureHpa, double eastPressureHpa,
            double northPressureHpa, double southPressureHpa, double cellSizeMeters, double dtSeconds,
            double roughness, double coriolisPerSecond, double pressureStrength) {
        double density = layer.pressureHpa() * 100.0 / (287.05 * (layer.temperatureCelsius() + 273.15));
        double accelerationX = (westPressureHpa - eastPressureHpa) * 100.0
                / (2.0 * cellSizeMeters * density) * pressureStrength;
        double accelerationZ = (northPressureHpa - southPressureHpa) * 100.0
                / (2.0 * cellSizeMeters * density) * pressureStrength;
        double drag = layer.heightMetres() < 100 ? 0.008 + 0.028 * roughness : 0.0008;
        double decay = Math.exp(-drag * dtSeconds);
        double response = -Math.expm1(-drag * dtSeconds) / drag;
        double x = layer.windXMetresPerSecond() * decay + accelerationX * response;
        double z = layer.windZMetresPerSecond() * decay + accelerationZ * response;
        // Rotation conserves speed; damping is stronger near the ground.
        double angle = coriolisPerSecond * dtSeconds;
        return new WindVector(x * Math.cos(angle) + z * Math.sin(angle),
                z * Math.cos(angle) - x * Math.sin(angle)).limited(AtmosphericUnits.MAX_WIND_METRES_PER_SECOND);
    }

    /** Signed terrain lift: downslope descent warms/dries rather than creating windward uplift. */
    public static double orographicVelocity(AtmosphereEnvironment environment, AtmosphericLayer wind) {
        return AtmosphericUnits.clamp(wind.windXMetresPerSecond() * environment.terrainGradientX()
                + wind.windZMetresPerSecond() * environment.terrainGradientZ(), -15.0, 15.0);
    }
}
