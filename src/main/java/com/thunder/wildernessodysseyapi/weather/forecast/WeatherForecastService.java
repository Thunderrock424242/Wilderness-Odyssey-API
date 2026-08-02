package com.thunder.wildernessodysseyapi.weather.forecast;

import com.thunder.wildernessodysseyapi.weather.api.WeatherForecast;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherHazardModel;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;

import java.util.List;

/** Pure forecasting model using pressure tendency and approaching tracked systems. */
public final class WeatherForecastService {

    private WeatherForecastService() {
    }

    /** Builds a forecast without mutating the authoritative tracker. */
    public static WeatherForecast forecast(
            WeatherSample current,
            double pressureTrend,
            double blockX,
            double blockZ,
            List<TrackedWeatherSystem> systems,
            double movementBlocksPerSecond
    ) {
        WeatherSample weather = current == null ? WeatherSample.CLEAR : current;
        TrackedWeatherSystem nearest = null;
        double nearestEdge = Double.POSITIVE_INFINITY;
        double bestApproach = 0.0;
        for (TrackedWeatherSystem system : systems == null ? List.<TrackedWeatherSystem>of() : systems) {
            double dx = blockX - system.centerX();
            double dz = blockZ - system.centerZ();
            double distance = Math.hypot(dx, dz);
            double edge = Math.max(0.0, distance - system.radiusBlocks());
            double approach = distance <= 1.0 ? 1.0 : Math.max(0.0,
                    (dx * system.motion().x() + dz * system.motion().z()) / distance);
            if (approach >= 0.08 && edge < nearestEdge) {
                nearest = system;
                nearestEdge = edge;
                bestApproach = approach;
            }
        }

        WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(weather);
        if (nearest == null) {
            double confidence = Math.min(0.72, 0.34 + Math.abs(pressureTrend) * 2.0);
            return new WeatherForecast(
                    hazards.dominant(), hazards.dominantIntensity(), pressureTrend, weather.wind(),
                    null, Double.POSITIVE_INFINITY, Long.MAX_VALUE, confidence
            );
        }

        double speed = Math.max(0.05, movementBlocksPerSecond * bestApproach);
        long etaTicks = Math.min(1_728_000L, Math.round(nearestEdge / speed * 20.0));
        double rangeConfidence = 1.0 - Math.min(1.0, nearestEdge / 4_096.0);
        double confidence = 0.42 + rangeConfidence * 0.34
                + nearest.intensity() * 0.16 + Math.min(0.08, Math.abs(pressureTrend));
        return new WeatherForecast(
                hazards.dominant(), hazards.dominantIntensity(), pressureTrend, weather.wind(),
                nearest.type(), nearestEdge, etaTicks, confidence
        );
    }
}
