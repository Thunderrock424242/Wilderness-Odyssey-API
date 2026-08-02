package com.thunder.wildernessodysseyapi.weather.system;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;

/** Applies bounded feedback from persistent weather identities to one cell. */
public final class WeatherSystemInfluenceModel {

    private WeatherSystemInfluenceModel() {
    }

    /** Returns a new sample with gradual system pressure, lift, wind, and storm support. */
    public static WeatherSample apply(
            WeatherSample sample,
            WeatherSystemTracker.SystemInfluence influence,
            double amount
    ) {
        WeatherSample weather = sample == null ? WeatherSample.CLEAR : sample;
        WeatherSystemTracker.SystemInfluence system = influence == null
                ? WeatherSystemTracker.SystemInfluence.NONE
                : influence;
        double scale = Math.max(0.0, Math.min(1.0, Double.isFinite(amount) ? amount : 0.0));
        if (scale <= 0.0) {
            return weather;
        }
        WindVector wind = new WindVector(
                weather.wind().x() + system.wind().x() * 0.12 * scale,
                weather.wind().z() + system.wind().z() * 0.12 * scale
        ).limited(1.0);
        return new WeatherSample(
                weather.temperature(),
                weather.humidity(),
                weather.pressure() - system.pressureDrop() * 0.12 * scale,
                wind,
                weather.cloudWater(),
                Math.min(1.0, weather.instability() + system.stormBoost() * 0.035 * scale),
                Math.min(1.0, weather.stormEnergy() + system.stormBoost() * 0.045 * scale),
                weather.precipitationIntensity(),
                weather.precipitationType(),
                Math.max(-1.0, Math.min(1.0,
                        weather.verticalMotion() + system.lift() * 0.055 * scale)),
                Math.min(1.0, weather.cloudDepth() + system.lift() * 0.030 * scale),
                WindVector.lerp(weather.cloudWind(), wind, 0.20 * scale),
                weather.surface()
        );
    }
}
