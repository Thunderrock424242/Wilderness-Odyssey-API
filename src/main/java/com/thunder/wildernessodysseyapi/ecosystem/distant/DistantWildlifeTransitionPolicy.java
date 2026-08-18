package com.thunder.wildernessodysseyapi.ecosystem.distant;

/** Pure distance and fade rules shared by transitions, rendering, and tests. */
public final class DistantWildlifeTransitionPolicy {

    private DistantWildlifeTransitionPolicy() {
    }

    /** Classifies one distance using the configured real, transition, and distant bands. */
    public static LodState lodState(
            double distance,
            int realEntityDistance,
            int distantWildlifeDistance,
            int transitionBuffer
    ) {
        double safeDistance = Math.max(0.0, distance);
        if (safeDistance < realEntityDistance) {
            return LodState.REAL;
        }
        if (safeDistance < realEntityDistance + transitionBuffer) {
            return LodState.TRANSITION;
        }
        if (safeDistance < distantWildlifeDistance - transitionBuffer) {
            return LodState.DISTANT;
        }
        if (safeDistance < distantWildlifeDistance) {
            return LodState.DISTANT_FADE;
        }
        return LodState.HIDDEN;
    }

    /** Returns a smooth representation alpha for both transition boundaries. */
    public static float renderAlpha(
            double distance,
            int realEntityDistance,
            int distantWildlifeDistance,
            int transitionBuffer
    ) {
        LodState state = lodState(distance, realEntityDistance, distantWildlifeDistance, transitionBuffer);
        return switch (state) {
            case REAL, HIDDEN -> 0.0F;
            case DISTANT -> 1.0F;
            case TRANSITION -> smoothStep((distance - realEntityDistance) / transitionBuffer);
            case DISTANT_FADE -> 1.0F - smoothStep(
                    (distance - (distantWildlifeDistance - transitionBuffer)) / transitionBuffer
            );
        };
    }

    /** Starts replacement work early enough to fill a herd across the transition band. */
    public static boolean shouldMaterialize(double closestPlayerDistance, int realDistance, int transitionBuffer) {
        return closestPlayerDistance <= realDistance + transitionBuffer;
    }

    /** Requires both configured separation and a sustained unobserved period before absorption. */
    public static boolean canAbstract(
            double closestPlayerDistance,
            boolean potentiallyObserved,
            long unobservedTicks,
            int realDistance,
            int transitionBuffer,
            long minimumUnobservedTicks
    ) {
        return closestPlayerDistance > realDistance + transitionBuffer
                && !potentiallyObserved
                && unobservedTicks >= minimumUnobservedTicks;
    }

    private static float smoothStep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return (float) (clamped * clamped * (3.0 - 2.0 * clamped));
    }

    /** Player-facing level-of-detail state for diagnostics. */
    public enum LodState {
        REAL,
        TRANSITION,
        DISTANT,
        DISTANT_FADE,
        HIDDEN
    }
}
