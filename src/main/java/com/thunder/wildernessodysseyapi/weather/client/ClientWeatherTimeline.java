package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudFieldSample;

/**
 * Pure timing and bounded-prediction rules for client weather presentation.
 *
 * <p>The server remains authoritative. The client uses the interval between
 * authoritative server ticks as its visual transition duration, then permits
 * only a small linear projection while a slightly late packet is in flight.</p>
 */
public final class ClientWeatherTimeline {

    static final long TICK_NANOS = 50_000_000L;
    static final long FALLBACK_DURATION_NANOS = 3_000_000_000L;
    static final long MIN_DURATION_NANOS = 250_000_000L;
    static final long MAX_DURATION_NANOS = 6_000_000_000L;
    public static final double MAX_EXTRAPOLATION = 0.35D;

    private ClientWeatherTimeline() {
    }

    /** Estimates the next visual interval from server time, softened by observed packet cadence. */
    public static long transitionDurationNanos(
            long previousServerTick,
            long currentServerTick,
            long observedArrivalNanos
    ) {
        long authoritative = FALLBACK_DURATION_NANOS;
        if (previousServerTick >= 0L && currentServerTick > previousServerTick) {
            long deltaTicks = currentServerTick - previousServerTick;
            authoritative = deltaTicks > Long.MAX_VALUE / TICK_NANOS
                    ? MAX_DURATION_NANOS
                    : deltaTicks * TICK_NANOS;
        }
        long observed = observedArrivalNanos > 0L ? observedArrivalNanos : authoritative;
        double blended = authoritative * 0.80D + observed * 0.20D;
        return clamp((long) blended, MIN_DURATION_NANOS, MAX_DURATION_NANOS);
    }

    /** Returns an interpolation amount in {@code [0, 1]} plus bounded late-packet projection. */
    public static double amount(long nowNanos, long transitionStartNanos, long durationNanos) {
        long safeDuration = clamp(durationNanos, MIN_DURATION_NANOS, MAX_DURATION_NANOS);
        double elapsed = Math.max(0L, nowNanos - transitionStartNanos);
        return Math.min(1.0D + MAX_EXTRAPOLATION, elapsed / safeDuration);
    }

    /** Returns only the controlled projection fraction beyond the newest snapshot. */
    public static double extrapolation(double amount) {
        return clamp(finiteOr(amount, 0.0D) - 1.0D, 0.0D, MAX_EXTRAPOLATION);
    }

    /** Interpolates, then briefly projects continuous fields without predicting categorical gameplay state. */
    public static WeatherSample sample(WeatherSample from, WeatherSample to, double amount) {
        WeatherSample start = from == null ? WeatherSample.CLEAR : from;
        WeatherSample end = to == null ? WeatherSample.CLEAR : to;
        double safeAmount = clamp(finiteOr(amount, 0.0D), 0.0D, 1.0D + MAX_EXTRAPOLATION);
        if (safeAmount <= 1.0D) {
            return WeatherSample.interpolate(start, end, safeAmount);
        }
        double extra = safeAmount - 1.0D;
        return new WeatherSample(
                project(start.temperature(), end.temperature(), extra),
                project(start.humidity(), end.humidity(), extra),
                project(start.pressure(), end.pressure(), extra),
                project(start.wind(), end.wind(), extra),
                project(start.cloudWater(), end.cloudWater(), extra),
                project(start.instability(), end.instability(), extra),
                project(start.stormEnergy(), end.stormEnergy(), extra),
                project(start.precipitationIntensity(), end.precipitationIntensity(), extra),
                end.precipitationType(),
                project(start.verticalMotion(), end.verticalMotion(), extra),
                project(start.cloudDepth(), end.cloudDepth(), extra),
                project(start.cloudWind(), end.cloudWind(), extra),
                project(start.surface(), end.surface(), extra)
        );
    }

    /** Applies the same support-aware timeline to the allocation-light cloud field. */
    public static CloudFieldSample cloud(CloudFieldSample from, CloudFieldSample to, double amount) {
        CloudFieldSample start = from == null ? CloudFieldSample.CLEAR : from;
        CloudFieldSample end = to == null ? CloudFieldSample.CLEAR : to;
        double safeAmount = clamp(finiteOr(amount, 0.0D), 0.0D, 1.0D + MAX_EXTRAPOLATION);
        if (safeAmount <= 1.0D) {
            return CloudFieldSample.interpolate(start, end, safeAmount);
        }
        double extra = safeAmount - 1.0D;
        return new CloudFieldSample(
                project(start.cloudWater(), end.cloudWater(), extra),
                project(start.precipitationIntensity(), end.precipitationIntensity(), extra),
                project(start.stormEnergy(), end.stormEnergy(), extra),
                project(start.instability(), end.instability(), extra),
                project(start.temperature(), end.temperature(), extra),
                project(start.humidity(), end.humidity(), extra),
                project(start.verticalMotion(), end.verticalMotion(), extra),
                project(start.cloudDepth(), end.cloudDepth(), extra),
                project(start.windX(), end.windX(), extra),
                project(start.windZ(), end.windZ(), extra),
                project(start.cloudWindX(), end.cloudWindX(), extra),
                project(start.cloudWindZ(), end.cloudWindZ(), extra),
                project(start.support(), end.support(), extra)
        );
    }

    /** Projects a scalar through the same bounded client timeline. */
    public static double scalar(double from, double to, double amount) {
        double safeAmount = clamp(finiteOr(amount, 0.0D), 0.0D, 1.0D + MAX_EXTRAPOLATION);
        return safeAmount <= 1.0D
                ? from + (to - from) * safeAmount
                : project(from, to, safeAmount - 1.0D);
    }

    private static WindVector project(WindVector from, WindVector to, double extra) {
        WindVector start = from == null ? WindVector.ZERO : from;
        WindVector end = to == null ? WindVector.ZERO : to;
        return new WindVector(
                project(start.x(), end.x(), extra),
                project(start.z(), end.z(), extra)
        );
    }

    private static SurfaceWeatherState project(
            SurfaceWeatherState from,
            SurfaceWeatherState to,
            double extra
    ) {
        SurfaceWeatherState start = from == null ? SurfaceWeatherState.DRY : from;
        SurfaceWeatherState end = to == null ? SurfaceWeatherState.DRY : to;
        return new SurfaceWeatherState(
                project(start.wetness(), end.wetness(), extra),
                project(start.puddleCoverage(), end.puddleCoverage(), extra),
                project(start.snowpack(), end.snowpack(), extra),
                project(start.frozenFraction(), end.frozenFraction(), extra)
        );
    }

    private static double project(double from, double to, double extra) {
        return to + (to - from) * clamp(extra, 0.0D, MAX_EXTRAPOLATION);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
