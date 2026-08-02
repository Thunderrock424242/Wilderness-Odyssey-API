package com.thunder.wildernessodysseyapi.weather.api;

import java.util.Objects;

/**
 * Immutable, client-safe localized weather sample.
 *
 * <p>Temperature is degrees Celsius, pressure is normalized around {@code 1.0},
 * and humidity, cloud water, instability, storm energy, and precipitation use
 * normalized {@code [0, 1]} units. Wind and vertical-motion components use
 * {@code [-1, 1]}.</p>
 *
 * @param temperature local air temperature in degrees Celsius
 * @param humidity relative humidity
 * @param pressure normalized atmospheric pressure
 * @param wind normalized horizontal wind
 * @param cloudWater condensed cloud moisture
 * @param instability convective instability
 * @param stormEnergy accumulated severe-weather energy
 * @param precipitationIntensity current localized precipitation intensity
 * @param precipitationType current localized precipitation form
 * @param verticalMotion normalized convective ascent or subsidence
 * @param cloudDepth normalized vertical cloud development
 * @param cloudWind normalized wind at cloud level
 * @param surface persistent normalized ground response beneath the cell
 */
public record WeatherSample(
        double temperature,
        double humidity,
        double pressure,
        WindVector wind,
        double cloudWater,
        double instability,
        double stormEnergy,
        double precipitationIntensity,
        PrecipitationType precipitationType,
        double verticalMotion,
        double cloudDepth,
        WindVector cloudWind,
        SurfaceWeatherState surface
) {
    public static final double MIN_TEMPERATURE = -80.0;
    public static final double MAX_TEMPERATURE = 60.0;
    public static final double MIN_PRESSURE = 0.5;
    public static final double MAX_PRESSURE = 1.5;
    public static final double MAX_WIND_COMPONENT = 1.0;
    public static final double SNOW_MAX_TEMPERATURE = 1.5;

    public static final WeatherSample CLEAR = new WeatherSample(
            15.0,
            0.35,
            1.0,
            WindVector.ZERO,
            0.0,
            0.15,
            0.0,
            0.0,
            PrecipitationType.NONE,
            0.0,
            0.0,
            WindVector.ZERO,
            SurfaceWeatherState.DRY
    );

    /**
     * Backward-compatible constructor for integrations that provide only the
     * original horizontal atmosphere fields.
     */
    public WeatherSample(
            double temperature,
            double humidity,
            double pressure,
            WindVector wind,
            double cloudWater,
            double instability,
            double stormEnergy,
            double precipitationIntensity,
            PrecipitationType precipitationType
    ) {
        this(
                temperature,
                humidity,
                pressure,
                wind,
                cloudWater,
                instability,
                stormEnergy,
                precipitationIntensity,
                precipitationType,
                0.0,
                unit(cloudWater * 0.55 + instability * 0.25 + stormEnergy * 0.35),
                wind,
                SurfaceWeatherState.DRY
        );
    }

    /** Retains the version-two construction shape without surface memory. */
    public WeatherSample(
            double temperature,
            double humidity,
            double pressure,
            WindVector wind,
            double cloudWater,
            double instability,
            double stormEnergy,
            double precipitationIntensity,
            PrecipitationType precipitationType,
            double verticalMotion,
            double cloudDepth,
            WindVector cloudWind
    ) {
        this(
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
                cloudWind,
                SurfaceWeatherState.DRY
        );
    }

    public WeatherSample {
        temperature = clamp(finiteOr(temperature, CLEAR_TEMPERATURE), MIN_TEMPERATURE, MAX_TEMPERATURE);
        humidity = unit(humidity);
        pressure = clamp(finiteOr(pressure, 1.0), MIN_PRESSURE, MAX_PRESSURE);
        WindVector safeWind = Objects.requireNonNullElse(wind, WindVector.ZERO);
        wind = new WindVector(
                clamp(finiteOr(safeWind.x(), 0.0), -MAX_WIND_COMPONENT, MAX_WIND_COMPONENT),
                clamp(finiteOr(safeWind.z(), 0.0), -MAX_WIND_COMPONENT, MAX_WIND_COMPONENT)
        );
        cloudWater = unit(cloudWater);
        instability = unit(instability);
        stormEnergy = unit(stormEnergy);
        precipitationIntensity = unit(precipitationIntensity);
        precipitationType = Objects.requireNonNullElse(precipitationType, PrecipitationType.NONE);
        if (precipitationIntensity == 0.0) {
            precipitationType = PrecipitationType.NONE;
        }
        verticalMotion = clamp(finiteOr(verticalMotion, 0.0), -1.0, 1.0);
        cloudDepth = unit(cloudDepth);
        WindVector safeCloudWind = Objects.requireNonNullElse(cloudWind, wind);
        cloudWind = new WindVector(
                clamp(finiteOr(safeCloudWind.x(), wind.x()), -MAX_WIND_COMPONENT, MAX_WIND_COMPONENT),
                clamp(finiteOr(safeCloudWind.z(), wind.z()), -MAX_WIND_COMPONENT, MAX_WIND_COMPONENT)
        );
        surface = Objects.requireNonNullElse(surface, SurfaceWeatherState.DRY);
    }

    /** Returns whether this position currently has any measurable precipitation. */
    public boolean hasPrecipitation() {
        return precipitationType != PrecipitationType.NONE && precipitationIntensity > 0.0;
    }

    /** Returns whether localized rain is active at this sample. */
    public boolean isRaining() {
        return (precipitationType == PrecipitationType.RAIN
                || precipitationType == PrecipitationType.HAIL)
                && precipitationIntensity > 0.0;
    }

    /** Returns whether localized snow is active at this sample. */
    public boolean isSnowing() {
        return precipitationType == PrecipitationType.SNOW && precipitationIntensity > 0.0;
    }

    /** Returns whether hail is active at this sample. */
    public boolean isHailing() {
        return precipitationType == PrecipitationType.HAIL && precipitationIntensity > 0.0;
    }

    /**
     * Returns a normalized local sky-darkening contribution.
     *
     * <p>Cloud cover supplies the broad overcast response while precipitation
     * and stored storm energy deepen active storm cells.</p>
     */
    public double skyDarkening() {
        return unit(cloudWater * 0.35 + precipitationIntensity * 0.45 + stormEnergy * 0.35);
    }

    /**
     * Returns a normalized local fog contribution for the client coordinator.
     */
    public double fogContribution() {
        double humidAir = Math.max(0.0, humidity - 0.70) / 0.30;
        return unit(humidAir * 0.20 + cloudWater * 0.15 + precipitationIntensity * 0.40);
    }

    /**
     * Returns normalized thunder strength derived from precipitation,
     * instability, and accumulated storm energy.
     */
    public double thunderIntensity() {
        if (!hasPrecipitation()) {
            return 0.0;
        }
        return unit(precipitationIntensity * (stormEnergy * 0.70 + instability * 0.30));
    }

    /** Returns whether this local storm is strong enough to request lightning. */
    public boolean lightningEligible() {
        return precipitationIntensity >= 0.25 && stormEnergy >= 0.55 && thunderIntensity() >= 0.35;
    }

    /** Returns a bounded dew-point estimate used by cloud-base presentation. */
    public double dewPointCelsius() {
        double safeHumidity = Math.max(0.01, humidity);
        double gamma = Math.log(safeHumidity) + 17.625 * temperature / (243.04 + temperature);
        return clamp(243.04 * gamma / (17.625 - gamma), MIN_TEMPERATURE, MAX_TEMPERATURE);
    }

    /** Returns the normalized difference between surface and cloud-level wind. */
    public double windShear() {
        return clamp(Math.hypot(
                cloudWind.x() - wind.x(),
                cloudWind.z() - wind.z()
        ), 0.0, 1.5);
    }

    /** Returns the dominant meteorological cloud genus in this sample. */
    public CloudType cloudType() {
        return CloudTypeClassifier.classify(this);
    }

    /** Returns the lifecycle implied by vertical development and precipitation. */
    public StormStage stormStage() {
        if (stormEnergy < 0.12 && cloudWater < 0.20) {
            return StormStage.CALM;
        }
        if (precipitationIntensity >= 0.20 && stormEnergy >= 0.42) {
            return StormStage.MATURE;
        }
        if (verticalMotion > 0.08 && instability >= 0.32) {
            return StormStage.DEVELOPING;
        }
        return StormStage.DISSIPATING;
    }

    /** Smoothly interpolates every continuous field between two samples. */
    public static WeatherSample interpolate(WeatherSample from, WeatherSample to, double alpha) {
        WeatherSample safeFrom = Objects.requireNonNullElse(from, CLEAR);
        WeatherSample safeTo = Objects.requireNonNullElse(to, CLEAR);
        double t = clamp(finiteOr(alpha, 0.0), 0.0, 1.0);
        double temperature = lerp(safeFrom.temperature, safeTo.temperature, t);
        double intensity = lerp(safeFrom.precipitationIntensity, safeTo.precipitationIntensity, t);
        PrecipitationType type = interpolateType(safeFrom, safeTo, temperature, intensity, t);
        return new WeatherSample(
                temperature,
                lerp(safeFrom.humidity, safeTo.humidity, t),
                lerp(safeFrom.pressure, safeTo.pressure, t),
                WindVector.lerp(safeFrom.wind, safeTo.wind, t),
                lerp(safeFrom.cloudWater, safeTo.cloudWater, t),
                lerp(safeFrom.instability, safeTo.instability, t),
                lerp(safeFrom.stormEnergy, safeTo.stormEnergy, t),
                intensity,
                type,
                lerp(safeFrom.verticalMotion, safeTo.verticalMotion, t),
                lerp(safeFrom.cloudDepth, safeTo.cloudDepth, t),
                WindVector.lerp(safeFrom.cloudWind, safeTo.cloudWind, t),
                SurfaceWeatherState.interpolate(safeFrom.surface, safeTo.surface, t)
        );
    }

    /** Alias retained for concise interpolation call sites. */
    public static WeatherSample lerp(WeatherSample from, WeatherSample to, double alpha) {
        return interpolate(from, to, alpha);
    }

    private static PrecipitationType interpolateType(
            WeatherSample from,
            WeatherSample to,
            double temperature,
            double intensity,
            double alpha
    ) {
        if (intensity == 0.0) {
            return PrecipitationType.NONE;
        }
        if (from.precipitationType == to.precipitationType) {
            return from.precipitationType;
        }
        if (from.precipitationType == PrecipitationType.HAIL
                || to.precipitationType == PrecipitationType.HAIL) {
            return alpha < 0.5 ? from.precipitationType : to.precipitationType;
        }
        if (from.precipitationType == PrecipitationType.NONE) {
            return to.precipitationType;
        }
        if (to.precipitationType == PrecipitationType.NONE) {
            return from.precipitationType;
        }
        if (from.precipitationType == PrecipitationType.SNOW
                && to.precipitationType == PrecipitationType.RAIN) {
            return temperature <= SNOW_MAX_TEMPERATURE ? PrecipitationType.SNOW : PrecipitationType.RAIN;
        }
        if (from.precipitationType == PrecipitationType.RAIN
                && to.precipitationType == PrecipitationType.SNOW) {
            return temperature <= SNOW_MAX_TEMPERATURE ? PrecipitationType.SNOW : PrecipitationType.RAIN;
        }
        return alpha < 0.5 ? from.precipitationType : to.precipitationType;
    }

    private static final double CLEAR_TEMPERATURE = 15.0;

    private static double unit(double value) {
        return clamp(finiteOr(value, 0.0), 0.0, 1.0);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
