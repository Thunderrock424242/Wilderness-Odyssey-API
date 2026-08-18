package com.thunder.wildernessodysseyapi.weather.client.audio;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import com.thunder.wildernessodysseyapi.weather.networking.DistantThunderSystemSyncPayload;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;

import java.util.List;
import java.util.Objects;

/**
 * Pure qualification, selection, attenuation, and cadence rules for distant thunder.
 *
 * <p>Rain type alone is deliberately insufficient. A candidate must also meet
 * meaningful precipitation, storm-energy, convective-instability, and thunder
 * potential thresholds before client audio can use it.</p>
 */
public final class DistantThunderModel {

    public static final double MINIMUM_CONVECTIVE_PRECIPITATION = 0.25;
    public static final double MINIMUM_STORM_ENERGY = 0.55;
    public static final double MINIMUM_INSTABILITY = 0.35;
    public static final double MINIMUM_THUNDER_POTENTIAL = 0.35;

    private static final double APPROACH_EPSILON = 0.12;
    private static final double MOVING_AWAY_RANGE_FRACTION = 0.60;
    private static final double CACHED_SELECTION_HYSTERESIS = 0.84;
    private static final double SYNCHRONIZED_UNIT_TOLERANCE = 0.5 / 255.0 + 1.0E-9;

    private DistantThunderModel() {
    }

    /**
     * Returns whether one server-authored storm has true thunderstorm audio potential.
     *
     * <p>This is the trust boundary that keeps light and gentle rain peaceful.
     * Even a tracked {@link WeatherSystemType#STORM} is rejected unless its
     * current authoritative cell has sufficiently convective rain or hail.</p>
     */
    public static boolean canProduceThunderstormAudio(
            DistantThunderSystemSyncPayload.StormSnapshot storm,
            WeatherRenderingConfig.Settings settings
    ) {
        if (storm == null || settings == null || !storm.type().storm()) {
            return false;
        }
        boolean convectivePrecipitation = storm.precipitationType() == PrecipitationType.RAIN
                || storm.precipitationType() == PrecipitationType.HAIL;
        return convectivePrecipitation
                && meetsSynchronizedThreshold(storm.intensity(), settings.minimumStormIntensity())
                && meetsSynchronizedThreshold(
                        storm.precipitationIntensity(), MINIMUM_CONVECTIVE_PRECIPITATION)
                && meetsSynchronizedThreshold(storm.stormEnergy(), MINIMUM_STORM_ENERGY)
                && meetsSynchronizedThreshold(storm.instability(), MINIMUM_INSTABILITY)
                && meetsSynchronizedThreshold(storm.thunderPotential(), MINIMUM_THUNDER_POTENTIAL);
    }

    /** Selects at most one audible storm so overlapping systems cannot create audio chaos. */
    public static Selection select(
            List<DistantThunderSystemSyncPayload.StormSnapshot> storms,
            double listenerX,
            double listenerZ,
            WeatherSample localWeather,
            WeatherRenderingConfig.Settings settings,
            long cachedStormId
    ) {
        WeatherRenderingConfig.Settings safeSettings = Objects.requireNonNull(settings, "settings");
        if (!safeSettings.distantThunderEnabled()
                || Objects.requireNonNullElse(localWeather, WeatherSample.CLEAR).lightningEligible()) {
            return null;
        }

        Selection best = null;
        Selection cached = null;
        List<DistantThunderSystemSyncPayload.StormSnapshot> safeStorms = storms == null ? List.of() : storms;
        for (DistantThunderSystemSyncPayload.StormSnapshot storm : safeStorms) {
            Selection candidate = evaluate(storm, listenerX, listenerZ, safeSettings);
            if (candidate == null) {
                continue;
            }
            if (storm.id() == cachedStormId) {
                cached = candidate;
            }
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        // A modest hysteresis band prevents two comparable storm cells from
        // swapping the directional sound origin every evaluation pass.
        if (cached != null && best != null && cached.score() >= best.score() * CACHED_SELECTION_HYSTERESIS) {
            return cached;
        }
        return best;
    }

    /** Returns a randomized bounded delay whose mean contracts as a storm approaches. */
    public static int nextIntervalTicks(
            Selection selection,
            WeatherRenderingConfig.Settings settings,
            double randomUnit
    ) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(settings, "settings");
        double maximumDistance = Math.max(1.0, settings.maximumAudibleDistance());
        double proximity = 1.0 - clamp(selection.distanceBlocks() / maximumDistance, 0.0, 1.0);
        double cadence = clamp(
                proximity * 0.65
                        + selection.storm().intensity() * 0.20
                        + selection.storm().thunderPotential() * 0.15,
                0.0,
                1.0
        );
        cadence *= switch (selection.storm().stage()) {
            case FORMING -> 0.82;
            case MATURE -> 1.0;
            case WEAKENING -> 0.68;
        };
        double seconds = lerp(
                settings.maximumThunderInterval(),
                settings.minimumThunderInterval(),
                cadence
        );
        if (selection.movement() == Movement.APPROACHING) {
            seconds *= 0.85;
        } else if (selection.movement() == Movement.MOVING_AWAY) {
            seconds *= 1.25;
        }
        double variation = 0.75 + clamp(randomUnit, 0.0, 1.0) * 0.50;
        double boundedSeconds = clamp(
                seconds * variation,
                settings.minimumThunderInterval(),
                settings.maximumThunderInterval()
        );
        return Math.max(1, (int) Math.round(boundedSeconds * 20.0));
    }

    private static Selection evaluate(
            DistantThunderSystemSyncPayload.StormSnapshot storm,
            double listenerX,
            double listenerZ,
            WeatherRenderingConfig.Settings settings
    ) {
        if (!canProduceThunderstormAudio(storm, settings)) {
            return null;
        }
        double deltaX = listenerX - storm.centerX();
        double deltaZ = listenerZ - storm.centerZ();
        double centerDistance = Math.hypot(deltaX, deltaZ);
        double distance = Math.max(0.0, centerDistance - storm.radiusBlocks());
        double maximumDistance = settings.maximumAudibleDistance();
        if (distance > maximumDistance) {
            return null;
        }

        Movement movement = movement(storm, deltaX, deltaZ, centerDistance);
        if (movement != Movement.APPROACHING
                && distance > maximumDistance * MOVING_AWAY_RANGE_FRACTION) {
            return null;
        }

        ThunderstormClassification classification = classify(storm);
        double proximity = 1.0 - clamp(distance / Math.max(1.0, maximumDistance), 0.0, 1.0);
        double motionScale = switch (movement) {
            case APPROACHING -> 1.0;
            case CROSSING_OR_STATIONARY -> 0.82;
            case MOVING_AWAY -> 0.55;
        };
        double classificationScale = switch (classification) {
            case THUNDERSTORM -> 1.0;
            case SEVERE_THUNDERSTORM -> 1.12;
            case EXTREME_STORM -> 1.20;
        };
        double lifecycleScale = switch (storm.stage()) {
            case FORMING -> 0.82;
            case MATURE -> 1.0;
            case WEAKENING -> 0.65;
        };
        double volume = Math.pow(proximity, 1.25)
                * (0.42 + storm.intensity() * 0.38 + storm.thunderPotential() * 0.20)
                * motionScale
                * classificationScale
                * lifecycleScale
                * settings.volumeMultiplier();
        volume = clamp(volume, 0.0, 1.5);
        if (volume < 0.002) {
            return null;
        }
        double score = volume
                * (0.70 + storm.intensity() * 0.30)
                * (movement == Movement.APPROACHING ? 1.12 : 1.0);
        return new Selection(storm, classification, movement, distance, volume, score);
    }

    private static Movement movement(
            DistantThunderSystemSyncPayload.StormSnapshot storm,
            double stormToListenerX,
            double stormToListenerZ,
            double centerDistance
    ) {
        double motionLength = Math.hypot(storm.motionX(), storm.motionZ());
        if (motionLength < 1.0E-4 || centerDistance < 1.0E-4) {
            return Movement.CROSSING_OR_STATIONARY;
        }
        double approach = (storm.motionX() / motionLength) * (stormToListenerX / centerDistance)
                + (storm.motionZ() / motionLength) * (stormToListenerZ / centerDistance);
        if (approach > APPROACH_EPSILON) {
            return Movement.APPROACHING;
        }
        if (approach < -APPROACH_EPSILON) {
            return Movement.MOVING_AWAY;
        }
        return Movement.CROSSING_OR_STATIONARY;
    }

    private static ThunderstormClassification classify(
            DistantThunderSystemSyncPayload.StormSnapshot storm
    ) {
        if ((storm.type().severe() && storm.intensity() >= 0.80)
                || (storm.intensity() >= 0.90 && storm.organization() >= 0.75)) {
            return ThunderstormClassification.EXTREME_STORM;
        }
        if (storm.type().severe()
                || (storm.intensity() >= 0.72 && storm.organization() >= 0.55)) {
            return ThunderstormClassification.SEVERE_THUNDERSTORM;
        }
        return ThunderstormClassification.THUNDERSTORM;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * clamp(amount, 0.0, 1.0);
    }

    private static boolean meetsSynchronizedThreshold(double value, double threshold) {
        return value + SYNCHRONIZED_UNIT_TOLERANCE >= threshold;
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }

    /** Audio-facing storm severity without changing the authoritative weather-system type. */
    public enum ThunderstormClassification {
        THUNDERSTORM,
        SEVERE_THUNDERSTORM,
        EXTREME_STORM
    }

    /** Kinematic relationship between one storm's synchronized motion and the listener. */
    public enum Movement {
        APPROACHING,
        CROSSING_OR_STATIONARY,
        MOVING_AWAY
    }

    /** One fully evaluated client-local candidate. */
    public record Selection(
            DistantThunderSystemSyncPayload.StormSnapshot storm,
            ThunderstormClassification classification,
            Movement movement,
            double distanceBlocks,
            double volume,
            double score
    ) {
    }
}
