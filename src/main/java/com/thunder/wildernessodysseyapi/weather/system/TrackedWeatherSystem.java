package com.thunder.wildernessodysseyapi.weather.system;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;

import java.util.Objects;

/**
 * Immutable persistent identity for a moving storm or atmospheric front.
 *
 * <p>Coordinates and radius are block units. Motion is normalized atmospheric
 * direction; the tracker applies configured block-per-second movement.</p>
 */
public record TrackedWeatherSystem(
        long id,
        WeatherSystemType type,
        WeatherSystemStage stage,
        double centerX,
        double centerZ,
        double radiusBlocks,
        double intensity,
        WindVector motion,
        double organization,
        long ageTicks,
        long lastUpdatedTick,
        long lastSplitTick
) {
    public TrackedWeatherSystem {
        if (id <= 0L) {
            throw new IllegalArgumentException("Weather-system id must be positive");
        }
        type = Objects.requireNonNullElse(type, WeatherSystemType.STORM);
        stage = Objects.requireNonNullElse(stage, WeatherSystemStage.FORMING);
        centerX = coordinate(centerX);
        centerZ = coordinate(centerZ);
        radiusBlocks = clamp(radiusBlocks, 16.0, 8_192.0);
        intensity = unit(intensity);
        motion = Objects.requireNonNullElse(motion, WindVector.ZERO).limited(1.0);
        organization = unit(organization);
        ageTicks = Math.max(0L, ageTicks);
        lastUpdatedTick = Math.max(0L, lastUpdatedTick);
        lastSplitTick = Math.max(0L, lastSplitTick);
    }

    /** Returns squared horizontal distance to a world position. */
    public double distanceSquared(double blockX, double blockZ) {
        double x = blockX - centerX;
        double z = blockZ - centerZ;
        return x * x + z * z;
    }

    private static double coordinate(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return clamp(finite, -30_000_000.0, 30_000_000.0);
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
