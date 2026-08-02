package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Persistent normalized surface response beneath one atmospheric cell.
 *
 * <p>Wetness and puddles are presentation inputs. Snowpack and frozen fraction
 * also drive the bounded server surface scheduler, while actual placed blocks
 * remain normal Minecraft world state.</p>
 */
public record SurfaceWeatherState(
        double wetness,
        double puddleCoverage,
        double snowpack,
        double frozenFraction
) {
    public static final SurfaceWeatherState DRY = new SurfaceWeatherState(0.0, 0.0, 0.0, 0.0);

    public SurfaceWeatherState {
        wetness = unit(wetness);
        puddleCoverage = Math.min(unit(puddleCoverage), wetness);
        snowpack = unit(snowpack);
        frozenFraction = unit(frozenFraction);
    }

    /** Smoothly blends synchronized surface memory for client presentation. */
    public static SurfaceWeatherState interpolate(
            SurfaceWeatherState from,
            SurfaceWeatherState to,
            double amount
    ) {
        SurfaceWeatherState start = from == null ? DRY : from;
        SurfaceWeatherState end = to == null ? DRY : to;
        double alpha = unit(amount);
        return new SurfaceWeatherState(
                lerp(start.wetness, end.wetness, alpha),
                lerp(start.puddleCoverage, end.puddleCoverage, alpha),
                lerp(start.snowpack, end.snowpack, alpha),
                lerp(start.frozenFraction, end.frozenFraction, alpha)
        );
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
