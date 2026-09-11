package com.thunder.wildernessodysseyapi.weather.simulation;

/** Immutable coarse layer; heights are metres above local terrain, winds are m/s. */
public record AtmosphericLayer(double heightMetres, double pressureHpa, double temperatureCelsius,
                               double relativeHumidity, double windXMetresPerSecond,
                               double windZMetresPerSecond) {
    public AtmosphericLayer {
        heightMetres = AtmosphericUnits.clamp(heightMetres, 0.0, 16000.0);
        pressureHpa = AtmosphericUnits.clamp(pressureHpa, 50.0, 1100.0);
        temperatureCelsius = AtmosphericUnits.clamp(temperatureCelsius, -100.0, 65.0);
        relativeHumidity = AtmosphericUnits.unit(relativeHumidity);
        windXMetresPerSecond = AtmosphericUnits.clamp(windXMetresPerSecond, -60.0, 60.0);
        windZMetresPerSecond = AtmosphericUnits.clamp(windZMetresPerSecond, -60.0, 60.0);
    }

    /** Wet-bulb approximation used along the falling hydrometeor path. */
    public double wetBulbCelsius() {
        return AtmosphericThermodynamics.wetBulbTemperature(temperatureCelsius, relativeHumidity);
    }

    public double windSpeedMetresPerSecond() {
        return Math.hypot(windXMetresPerSecond, windZMetresPerSecond);
    }
}
