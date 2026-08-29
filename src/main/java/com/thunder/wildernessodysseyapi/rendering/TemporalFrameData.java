package com.thunder.wildernessodysseyapi.rendering;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Backend-neutral inputs a future temporal upscaler may consume.
 *
 * <p>Current Minecraft 1.21.1 integration publishes empty image handles because
 * it does not expose motion vectors or a supported post-process handoff here.
 * Empty values are deliberate; the framework never fabricates buffer access.</p>
 */
public record TemporalFrameData(
        Optional<ImageHandle> color,
        Optional<ImageHandle> depth,
        Optional<ImageHandle> motionVectors,
        Optional<ImageHandle> reactiveMask,
        Optional<Jitter> jitter,
        OptionalDouble exposure,
        Resolution renderResolution,
        Resolution outputResolution,
        long frameTimeNanos
) {
    public TemporalFrameData {
        color = color == null ? Optional.empty() : color;
        depth = depth == null ? Optional.empty() : depth;
        motionVectors = motionVectors == null ? Optional.empty() : motionVectors;
        reactiveMask = reactiveMask == null ? Optional.empty() : reactiveMask;
        jitter = jitter == null ? Optional.empty() : jitter;
        exposure = exposure == null ? OptionalDouble.empty() : exposure;
        renderResolution = renderResolution == null ? Resolution.ONE : renderResolution;
        outputResolution = outputResolution == null ? renderResolution : outputResolution;
        frameTimeNanos = Math.max(0L, frameTimeNanos);
    }

    /** Returns an honest native-rendering frame with no temporal buffer claims. */
    public static TemporalFrameData unavailable(Resolution render, Resolution output, long frameTimeNanos) {
        return new TemporalFrameData(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDouble.empty(),
                render,
                output,
                frameTimeNanos
        );
    }

    /** Temporal reconstruction requires at least color, depth, motion, and jitter. */
    public boolean hasTemporalReconstructionInputs() {
        return color.isPresent() && depth.isPresent() && motionVectors.isPresent() && jitter.isPresent();
    }

    /** Marker implemented by backend-specific image wrappers, never raw cross-backend IDs. */
    public interface ImageHandle {
        String backendId();

        int width();

        int height();
    }

    public record Jitter(float x, float y) {
        public Jitter {
            x = Float.isFinite(x) ? x : 0.0F;
            y = Float.isFinite(y) ? y : 0.0F;
        }
    }

    public record Resolution(int width, int height) {
        public static final Resolution ONE = new Resolution(1, 1);

        public Resolution {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }
    }
}
