package com.thunder.wildernessodysseyapi.weather.forecast;

import com.thunder.wildernessodysseyapi.weather.api.WeatherThreat;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;

import java.util.List;

/**
 * Pure intersection forecast over the existing persistent weather identities.
 *
 * <p>The service projects each system along the same motion vector and speed
 * used by {@link WeatherSystemTracker}. It rejects paths that miss or move away
 * from the query point and discounts weakening systems over their ETA.</p>
 */
public final class WeatherThreatForecastService {

    private static final long MAXIMUM_LOOK_AHEAD_TICKS = 24_000L;

    private WeatherThreatForecastService() {
    }

    /** Returns the strongest system predicted to intersect the position in the requested window. */
    public static WeatherThreatForecast forecast(
            double blockX,
            double blockZ,
            int lookAheadTicks,
            List<TrackedWeatherSystem> systems,
            WeatherSystemTracker.TrackingSettings settings
    ) {
        WeatherSystemTracker.TrackingSettings controls = settings == null
                ? WeatherSystemTracker.TrackingSettings.DEFAULT
                : settings;
        long horizon = Math.max(0L, Math.min(MAXIMUM_LOOK_AHEAD_TICKS, lookAheadTicks));
        WeatherThreatForecast best = WeatherThreatForecast.NONE;
        for (TrackedWeatherSystem system : systems == null ? List.<TrackedWeatherSystem>of() : systems) {
            Intersection intersection = intersection(blockX, blockZ, system, controls.movementBlocksPerSecond());
            if (intersection == null || intersection.etaTicks() > horizon) {
                continue;
            }

            double projectedIntensity = projectedIntensity(system, intersection.etaTicks(), controls);
            WeatherThreat threat = classify(system.type(), system.stage(), projectedIntensity);
            if (threat == WeatherThreat.NONE) {
                continue;
            }
            double rangeConfidence = 1.0 - Math.min(1.0, intersection.distanceBlocks() / 4_096.0);
            double stageConfidence = system.stage() == WeatherSystemStage.WEAKENING ? 0.58 : 0.82;
            double confidence = 0.18 + rangeConfidence * 0.30
                    + projectedIntensity * 0.32 + stageConfidence * 0.20;
            WeatherThreatForecast candidate = new WeatherThreatForecast(
                    threat,
                    projectedIntensity,
                    intersection.distanceBlocks(),
                    intersection.etaTicks(),
                    confidence,
                    system.id(),
                    system.type(),
                    system.stage()
            );
            if (moreImportant(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static Intersection intersection(
            double blockX,
            double blockZ,
            TrackedWeatherSystem system,
            double movementBlocksPerSecond
    ) {
        double toTargetX = blockX - system.centerX();
        double toTargetZ = blockZ - system.centerZ();
        double distance = Math.hypot(toTargetX, toTargetZ);
        double edgeDistance = Math.max(0.0, distance - system.radiusBlocks());
        if (edgeDistance <= 0.0) {
            return new Intersection(0L, 0.0);
        }

        double motionMagnitude = system.motion().magnitude();
        double speed = Math.max(0.0, movementBlocksPerSecond) * motionMagnitude;
        if (speed <= 0.001) {
            return null;
        }
        double directionX = system.motion().x() / motionMagnitude;
        double directionZ = system.motion().z() / motionMagnitude;
        double alongPath = toTargetX * directionX + toTargetZ * directionZ;
        if (alongPath <= 0.0) {
            return null;
        }
        double perpendicularSquared = Math.max(0.0, distance * distance - alongPath * alongPath);
        double radiusSquared = system.radiusBlocks() * system.radiusBlocks();
        if (perpendicularSquared > radiusSquared) {
            return null;
        }

        double entryDistance = Math.max(0.0, alongPath - Math.sqrt(radiusSquared - perpendicularSquared));
        long etaTicks = Math.max(0L, Math.round(entryDistance / speed * 20.0));
        return new Intersection(etaTicks, edgeDistance);
    }

    private static double projectedIntensity(
            TrackedWeatherSystem system,
            long etaTicks,
            WeatherSystemTracker.TrackingSettings controls
    ) {
        if (system.stage() != WeatherSystemStage.WEAKENING) {
            return system.intensity();
        }
        double updatesUntilArrival = etaTicks / (double) Math.max(1, controls.nominalIntervalTicks());
        return Math.max(0.0, system.intensity() - controls.dissipationPerUpdate() * updatesUntilArrival);
    }

    private static WeatherThreat classify(
            WeatherSystemType system,
            WeatherSystemStage stage,
            double intensity
    ) {
        if (intensity < 0.15) {
            return WeatherThreat.NONE;
        }
        if (system.severe()) {
            if (intensity >= 0.72 && stage == WeatherSystemStage.MATURE) {
                return WeatherThreat.EXTREME_WEATHER;
            }
            if (intensity >= 0.58) {
                return WeatherThreat.SEVERE_STORM;
            }
            return intensity >= 0.42 ? WeatherThreat.THUNDERSTORM : WeatherThreat.RAIN;
        }
        if (system == WeatherSystemType.STORM) {
            if (intensity >= 0.78) {
                return WeatherThreat.SEVERE_STORM;
            }
            if (intensity >= 0.52) {
                return WeatherThreat.THUNDERSTORM;
            }
            if (intensity >= 0.32) {
                return WeatherThreat.RAIN;
            }
            return WeatherThreat.LIGHT_RAIN;
        }
        if ((system == WeatherSystemType.COLD_FRONT || system == WeatherSystemType.OCCLUDED_FRONT)
                && intensity >= 0.66) {
            return WeatherThreat.THUNDERSTORM;
        }
        return intensity >= 0.34 ? WeatherThreat.RAIN : WeatherThreat.LIGHT_RAIN;
    }

    private static boolean moreImportant(WeatherThreatForecast candidate, WeatherThreatForecast current) {
        int severity = Integer.compare(candidate.type().ordinal(), current.type().ordinal());
        if (severity != 0) {
            return severity > 0;
        }
        int intensity = Double.compare(candidate.intensity(), current.intensity());
        if (intensity != 0) {
            return intensity > 0;
        }
        if (candidate.estimatedArrivalTicks() != current.estimatedArrivalTicks()) {
            return candidate.estimatedArrivalTicks() < current.estimatedArrivalTicks();
        }
        return candidate.sourceSystemId() < current.sourceSystemId();
    }

    private record Intersection(long etaTicks, double distanceBlocks) {
    }
}
