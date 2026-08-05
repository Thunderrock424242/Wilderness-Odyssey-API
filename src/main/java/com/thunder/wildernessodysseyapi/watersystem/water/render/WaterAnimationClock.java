package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Defines immutable phase rates for every built-in water material oscillator.
 *
 * <p>The clock accepts the exact simulation tick plus render partial tick and
 * reduces the result modulo one turn. Environmental state intentionally has no
 * place in this API: sea energy, weather, camera location, and entity movement
 * may change material strength, but cannot rewrite an absolute-time phase.</p>
 */
final class WaterAnimationClock {

    private static final double[] SURFACE_RATES = {
            0.19, -0.16, 1.05, -0.80,
            3.05, 4.85, 1.15, 0.55
    };
    private static final double[] UNDERWATER_DISTORTION_RATES = {
            1.00, -0.83, 1.34, -1.12
    };
    private static final double[] UNDERWATER_FALLBACK_RATES = {
            1.05, -0.88
    };

    private WaterAnimationClock() {
    }

    /** Returns one immutable surface-material phase. */
    static float surfacePhase(long gameTime, float partialTick, int layer) {
        return stablePhase(gameTime, partialTick, SURFACE_RATES[layer]);
    }

    /** Returns one immutable underwater-distortion phase. */
    static float underwaterDistortionPhase(long gameTime, float partialTick, int layer) {
        return stablePhase(gameTime, partialTick, UNDERWATER_DISTORTION_RATES[layer]);
    }

    /** Returns one immutable no-scene-capture underwater phase. */
    static float underwaterFallbackPhase(long gameTime, float partialTick, int layer) {
        return stablePhase(gameTime, partialTick, UNDERWATER_FALLBACK_RATES[layer]);
    }

    /**
     * Reduces a constant-rate absolute simulation clock without converting its
     * long tick value to an imprecise float first.
     */
    static float stablePhase(
            long gameTime,
            float partialTick,
            double radiansPerSecond
    ) {
        long remainingTicks = Math.max(0L, gameTime);
        double safeRate = Double.isFinite(radiansPerSecond) ? radiansPerSecond : 0.0;
        double phaseStep = Math.IEEEremainder(safeRate / 20.0, Math.PI * 2.0);
        double phase = Math.IEEEremainder(
                clampPartialTick(partialTick) * phaseStep,
                Math.PI * 2.0
        );
        for (int digit = 0; digit < 7; digit++) {
            phase = Math.IEEEremainder(
                    phase + (remainingTicks & 1023L) * phaseStep,
                    Math.PI * 2.0
            );
            remainingTicks >>>= 10;
            phaseStep = Math.IEEEremainder(phaseStep * 1024.0, Math.PI * 2.0);
        }
        return (float) phase;
    }

    /** Returns a precision-stable fraction through an integer tick period. */
    static float periodicFraction(long gameTime, float partialTick, long periodTicks) {
        if (periodTicks <= 0L) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        long wrappedTick = Math.floorMod(gameTime, periodTicks);
        return (float) ((wrappedTick + (double) clampPartialTick(partialTick)) / periodTicks);
    }

    /** Bounds a render fraction to the current simulation tick. */
    static float clampPartialTick(float partialTick) {
        return Float.isFinite(partialTick)
                ? Math.max(0.0f, Math.min(1.0f, partialTick))
                : 0.0f;
    }
}
