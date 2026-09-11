package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import java.util.Objects;

/**
 * Server-owned physical column inventory, never reconstructed from render humidity after a step.
 * Pressure is sea-level-equivalent hPa for horizontal gradients; layer pressures are hydrostatic.
 * Vapor, liquid and ice are column kg/m2, numerically equal to millimetres of liquid water.
 */
public record AtmosphericPhysicalState(double temperatureCelsius, double pressureHpa,
        double vaporKgPerSquareMetre, double cloudLiquidKgPerSquareMetre, double cloudIceKgPerSquareMetre,
        AtmosphericColumn column, double verticalVelocityMetresPerSecond, double instabilityJoulesPerKg,
        double stormEnergy, double surfaceTemperatureCelsius, double precipitationMmPerHour,
        SurfaceWeatherState surface) {
    public AtmosphericPhysicalState {
        temperatureCelsius = AtmosphericUnits.clamp(temperatureCelsius, -80, 60);
        pressureHpa = AtmosphericUnits.clamp(pressureHpa, 900, 1100);
        requireWater(vaporKgPerSquareMetre);
        requireWater(cloudLiquidKgPerSquareMetre);
        requireWater(cloudIceKgPerSquareMetre);
        column = Objects.requireNonNull(column);
        verticalVelocityMetresPerSecond = AtmosphericUnits.clamp(verticalVelocityMetresPerSecond, -20, 50);
        instabilityJoulesPerKg = AtmosphericUnits.clamp(instabilityJoulesPerKg, 0, 6000);
        stormEnergy = AtmosphericUnits.unit(stormEnergy);
        surfaceTemperatureCelsius = AtmosphericUnits.clamp(surfaceTemperatureCelsius, -80, 65);
        precipitationMmPerHour = AtmosphericUnits.clamp(precipitationMmPerHour, 0, 500);
        surface = Objects.requireNonNullElse(surface, SurfaceWeatherState.DRY);
    }

    /** One-time default for v1-v3 saves, operator edits and legacy constructor callers. */
    public static AtmosphericPhysicalState fromLegacy(WeatherSample sample) {
        WeatherSample weather = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
        double pressure = AtmosphericUnits.REFERENCE_PRESSURE_HPA
                + (weather.pressure() - 1.0) * AtmosphericUnits.PRESSURE_SCALE_HPA;
        WindVector surfaceWind = new WindVector(weather.wind().x() * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND,
                weather.wind().z() * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND);
        WindVector upperWind = new WindVector(weather.cloudWind().x() * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND,
                weather.cloudWind().z() * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND);
        AtmosphericColumn column = AtmosphericColumn.initial(weather.temperature(), pressure,
                weather.humidity(), surfaceWind, upperWind);
        double cloud = weather.cloudWater() * AtmosphericUnits.CLOUD_SCALE_KG_PER_SQUARE_METRE;
        double iceFraction = AtmosphericUnits.unit(-column.middle().temperatureCelsius() / 20.0);
        return new AtmosphericPhysicalState(weather.temperature(), pressure,
                AtmosphericThermodynamics.vaporContent(weather.temperature(), weather.humidity())
                        * AtmosphericUnits.VAPOR_SCALE_KG_PER_SQUARE_METRE,
                cloud * (1.0 - iceFraction), cloud * iceFraction, column,
                weather.verticalMotion() * 20.0, weather.instability() * 3000.0, weather.stormEnergy(),
                weather.temperature(), weather.precipitationIntensity() * AtmosphericUnits.RAIN_SCALE_MM_PER_HOUR,
                weather.surface());
    }

    public double relativeHumidity() {
        return AtmosphericUnits.unit(vaporKgPerSquareMetre
                / AtmosphericThermodynamics.saturationColumnWater(temperatureCelsius));
    }

    /** Applies existing persistent-system dynamics without rebuilding or altering moisture inventory. */
    public AtmosphericPhysicalState withDynamics(WeatherSample feedback) {
        WeatherSample baseline = toWeatherSample(surface, feedback.precipitationType());
        AtmosphericLayer ground = column.surface();
        AtmosphericLayer top = column.upper();
        AtmosphericColumn altered = new AtmosphericColumn(new AtmosphericLayer(ground.heightMetres(),
                ground.pressureHpa(), ground.temperatureCelsius(), ground.relativeHumidity(),
                ground.windXMetresPerSecond() + (feedback.wind().x() - baseline.wind().x()) * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND,
                ground.windZMetresPerSecond() + (feedback.wind().z() - baseline.wind().z()) * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND), column.low(), column.middle(),
                new AtmosphericLayer(top.heightMetres(), top.pressureHpa(), top.temperatureCelsius(), top.relativeHumidity(),
                        top.windXMetresPerSecond() + (feedback.cloudWind().x() - baseline.cloudWind().x()) * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND,
                        top.windZMetresPerSecond() + (feedback.cloudWind().z() - baseline.cloudWind().z()) * AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND));
        return new AtmosphericPhysicalState(temperatureCelsius, pressureHpa
                + (feedback.pressure() - baseline.pressure()) * AtmosphericUnits.PRESSURE_SCALE_HPA, vaporKgPerSquareMetre,
                cloudLiquidKgPerSquareMetre, cloudIceKgPerSquareMetre, altered,
                verticalVelocityMetresPerSecond + (feedback.verticalMotion() - baseline.verticalMotion()) * 20,
                instabilityJoulesPerKg + (feedback.instability() - baseline.instability()) * 3000, feedback.stormEnergy(),
                surfaceTemperatureCelsius, precipitationMmPerHour, surface);
    }

    public double cloudWaterKgPerSquareMetre() {
        return cloudLiquidKgPerSquareMetre + cloudIceKgPerSquareMetre;
    }

    public double totalWaterKgPerSquareMetre() {
        return vaporKgPerSquareMetre + cloudWaterKgPerSquareMetre();
    }

    /** Immutable adapter; clipping here never removes mass from the physical owner. */
    public WeatherSample toWeatherSample(SurfaceWeatherState surfaceState, PrecipitationType type) {
        CloudProperties cloud = CloudProperties.derive(this);
        return new WeatherSample(temperatureCelsius, relativeHumidity(),
                1.0 + (pressureHpa - AtmosphericUnits.REFERENCE_PRESSURE_HPA) / AtmosphericUnits.PRESSURE_SCALE_HPA,
                new WindVector(column.surface().windXMetresPerSecond() / AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND,
                        column.surface().windZMetresPerSecond() / AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND),
                cloudWaterKgPerSquareMetre() / AtmosphericUnits.CLOUD_SCALE_KG_PER_SQUARE_METRE,
                instabilityJoulesPerKg / 3000.0, stormEnergy,
                precipitationMmPerHour / AtmosphericUnits.RAIN_SCALE_MM_PER_HOUR, type,
                verticalVelocityMetresPerSecond / 20.0, cloud.depthMetres() / 6000.0,
                new WindVector(column.upper().windXMetresPerSecond() / AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND,
                        column.upper().windZMetresPerSecond() / AtmosphericUnits.WIND_SCALE_METRES_PER_SECOND), surfaceState);
    }

    private static void requireWater(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Atmospheric water inventory must be finite and nonnegative");
        }
    }
}
