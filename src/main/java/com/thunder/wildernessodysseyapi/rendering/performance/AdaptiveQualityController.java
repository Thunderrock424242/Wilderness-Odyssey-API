package com.thunder.wildernessodysseyapi.rendering.performance;

import com.thunder.wildernessodysseyapi.rendering.RenderingQuality;

/**
 * Gradual frame-time controller with smoothing, hysteresis, warm-up, and cooldown.
 *
 * <p>The controller publishes only a transient ceiling. It never edits configs
 * or enables effects, and callers may apply a stricter feature-specific ceiling.</p>
 */
public final class AdaptiveQualityController {

    private static final int WARMUP_SAMPLES = 60;
    private static final double MOVING_AVERAGE_ALPHA = 0.08;
    private static final double REDUCE_THRESHOLD = 1.12;
    private static final double INCREASE_THRESHOLD = 0.78;
    private static final long MAX_SAMPLE_NANOS = 250_000_000L;

    private Policy activePolicy;
    private RenderingQuality quality = RenderingQuality.CINEMATIC;
    private double movingAverageNanos;
    private int samples;
    private long nextAdjustmentNanos;

    /** Records one completed in-world frame and returns the current ceiling. */
    public RenderingQuality recordFrame(long frameNanos, long nowNanos, Policy policy) {
        Policy safePolicy = policy == null ? Policy.DISABLED : policy;
        if (!safePolicy.enabled()) {
            resetDisabled();
            return quality;
        }
        if (!safePolicy.equals(activePolicy)) {
            boolean wasDisabled = activePolicy == null || !activePolicy.enabled();
            activePolicy = safePolicy;
            quality = wasDisabled
                    ? safePolicy.maximum()
                    : RenderingQuality.clamp(quality, safePolicy.minimum(), safePolicy.maximum());
            movingAverageNanos = 0.0;
            samples = 0;
            nextAdjustmentNanos = Math.max(0L, nowNanos) + safePolicy.cooldownNanos();
        }

        long boundedSample = Math.max(1L, Math.min(MAX_SAMPLE_NANOS, frameNanos));
        movingAverageNanos = samples == 0
                ? boundedSample
                : movingAverageNanos + (boundedSample - movingAverageNanos) * MOVING_AVERAGE_ALPHA;
        samples++;
        if (samples < WARMUP_SAMPLES || nowNanos < nextAdjustmentNanos) {
            return quality;
        }

        double target = safePolicy.targetFrameNanos();
        if (movingAverageNanos > target * REDUCE_THRESHOLD
                && quality.ordinal() > safePolicy.minimum().ordinal()) {
            quality = RenderingQuality.values()[quality.ordinal() - 1];
            nextAdjustmentNanos = nowNanos + safePolicy.cooldownNanos();
        } else if (movingAverageNanos < target * INCREASE_THRESHOLD
                && quality.ordinal() < safePolicy.maximum().ordinal()) {
            quality = RenderingQuality.values()[quality.ordinal() + 1];
            nextAdjustmentNanos = nowNanos + safePolicy.cooldownNanos();
        }
        return quality;
    }

    public RenderingQuality quality() {
        return quality;
    }

    public long movingAverageNanos() {
        return Math.max(0L, Math.round(movingAverageNanos));
    }

    private void resetDisabled() {
        activePolicy = Policy.DISABLED;
        quality = RenderingQuality.CINEMATIC;
        movingAverageNanos = 0.0;
        samples = 0;
        nextAdjustmentNanos = 0L;
    }

    /** Immutable controller policy assembled from client config. */
    public record Policy(
            boolean enabled,
            long targetFrameNanos,
            RenderingQuality minimum,
            RenderingQuality maximum,
            long cooldownNanos
    ) {
        public static final Policy DISABLED = new Policy(
                false,
                16_670_000L,
                RenderingQuality.LOW,
                RenderingQuality.CINEMATIC,
                5_000_000_000L
        );

        public Policy {
            targetFrameNanos = Math.max(1_000_000L, targetFrameNanos);
            minimum = minimum == null ? RenderingQuality.LOW : minimum;
            maximum = maximum == null ? RenderingQuality.CINEMATIC : maximum;
            if (minimum.ordinal() > maximum.ordinal()) {
                RenderingQuality swap = minimum;
                minimum = maximum;
                maximum = swap;
            }
            cooldownNanos = Math.max(0L, cooldownNanos);
        }
    }
}
