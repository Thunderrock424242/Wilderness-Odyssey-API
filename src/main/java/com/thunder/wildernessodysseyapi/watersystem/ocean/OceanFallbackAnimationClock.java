package com.thunder.wildernessodysseyapi.watersystem.ocean;

/**
 * Produces precision-stable periodic phases for vanilla-weather ocean motion.
 *
 * <p>The world tick remains a {@code long} until it is reduced by the requested
 * period. This preserves render partial ticks in very old worlds, where first
 * converting the absolute tick to a float would make gusts and wind heading
 * advance in visible steps.</p>
 */
final class OceanFallbackAnimationClock {

    private static final double TWO_PI = Math.PI * 2.0;

    private OceanFallbackAnimationClock() {
    }

    /** Returns a one-turn phase for an integer-length period measured in ticks. */
    static double periodicPhase(long gameTime, float partialTick, long periodTicks) {
        if (periodTicks <= 0L) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        long wrappedTick = Math.floorMod(gameTime, periodTicks);
        double frameTick = wrappedTick + clampPartialTick(partialTick);
        return frameTick * TWO_PI / periodTicks;
    }

    private static double clampPartialTick(float partialTick) {
        return Float.isFinite(partialTick)
                ? Math.max(0.0, Math.min(1.0, partialTick))
                : 0.0;
    }
}
