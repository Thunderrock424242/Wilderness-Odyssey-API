package com.thunder.wildernessodysseyapi.weather.simulation;

/**
 * Bounded thermodynamic approximations for the atmospheric grid.
 *
 * <p>The simulation stores relative humidity for API readability, while
 * transport operates on temperature-dependent vapor capacity. The functions
 * here are deterministic and allocation-free; they are not a full fluid or
 * radiative-transfer model.</p>
 */
public final class AtmosphericThermodynamics {

    private static final double MAGNUS_A = 17.625;
    private static final double MAGNUS_B = 243.04;
    private static final double REFERENCE_SATURATION_PRESSURE = saturationPressureRaw(35.0);

    private AtmosphericThermodynamics() {
    }

    /** Returns normalized saturation capacity for air at the supplied temperature. */
    public static double saturationCapacity(double temperatureCelsius) {
        double boundedTemperature = clamp(temperatureCelsius, -80.0, 60.0);
        return clamp(
                saturationPressureRaw(boundedTemperature) / REFERENCE_SATURATION_PRESSURE,
                0.002,
                2.5
        );
    }

    /** Converts relative humidity into a transportable normalized vapor inventory. */
    public static double vaporContent(double temperatureCelsius, double relativeHumidity) {
        return saturationCapacity(temperatureCelsius) * unit(relativeHumidity);
    }

    /** Restores relative humidity after vapor and temperature transport. */
    public static double relativeHumidity(double temperatureCelsius, double vaporContent) {
        return unit(Math.max(0.0, vaporContent) / saturationCapacity(temperatureCelsius));
    }

    /**
     * Approximates wet-bulb temperature for the rain/snow boundary.
     *
     * <p>This Stull-style approximation is accurate enough for visual and
     * gameplay precipitation classification over ordinary Minecraft climates.</p>
     */
    public static double wetBulbTemperature(double temperatureCelsius, double relativeHumidity) {
        double temperature = clamp(temperatureCelsius, -50.0, 50.0);
        double humidityPercent = clamp(relativeHumidity * 100.0, 1.0, 100.0);
        double wetBulb = temperature * Math.atan(0.151977 * Math.sqrt(humidityPercent + 8.313659))
                + Math.atan(temperature + humidityPercent)
                - Math.atan(humidityPercent - 1.676331)
                + 0.00391838 * Math.pow(humidityPercent, 1.5)
                * Math.atan(0.023101 * humidityPercent)
                - 4.686035;
        return clamp(wetBulb, -80.0, 60.0);
    }

    private static double saturationPressureRaw(double temperatureCelsius) {
        return Math.exp(MAGNUS_A * temperatureCelsius / (MAGNUS_B + temperatureCelsius));
    }

    private static double unit(double value) {
        return clamp(Double.isFinite(value) ? value : 0.0, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
