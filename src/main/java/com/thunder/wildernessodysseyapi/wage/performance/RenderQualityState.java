package com.thunder.wildernessodysseyapi.wage.performance;

import com.thunder.wildernessodysseyapi.wage.RenderingQuality;

/** Lock-free publication point for the current transient adaptive ceiling. */
public final class RenderQualityState {

    private static volatile Snapshot current = Snapshot.DISABLED;

    private RenderQualityState() {
    }

    public static RenderingQuality currentQuality() {
        return current.quality();
    }

    public static Snapshot snapshot() {
        return current;
    }

    /** Publishes one immutable result without changing any persisted setting. */
    public static void publish(boolean enabled, RenderingQuality quality, long averageFrameNanos) {
        current = new Snapshot(
                enabled,
                enabled && quality != null ? quality : RenderingQuality.CINEMATIC,
                Math.max(0L, averageFrameNanos)
        );
    }

    public record Snapshot(boolean enabled, RenderingQuality quality, long averageFrameNanos) {
        private static final Snapshot DISABLED = new Snapshot(
                false,
                RenderingQuality.CINEMATIC,
                0L
        );
    }
}
