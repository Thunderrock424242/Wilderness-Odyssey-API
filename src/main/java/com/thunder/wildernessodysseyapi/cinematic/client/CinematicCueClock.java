package com.thunder.wildernessodysseyapi.cinematic.client;

import net.minecraft.util.Mth;

/**
 * Converts server-authored cue times into client subtitle and audio offsets.
 *
 * <p>Narration, subtitles, and cue-bound effects use the same level game-time
 * clock as cinematic stages. Packet arrival time therefore cannot extend a
 * subtitle or restart an authored clip from the beginning after a delay.</p>
 */
public final class CinematicCueClock {
    private static final long MICROSECONDS_PER_TICK = 50_000L;

    private CinematicCueClock() {
    }

    public static int elapsedTicks(long gameTime, long cueStartGameTime, int durationTicks) {
        long elapsed = Math.max(0L, gameTime - cueStartGameTime);
        return (int) Math.min(Math.max(0, durationTicks), elapsed);
    }

    public static long audioOffsetMicroseconds(int elapsedTicks) {
        return Math.max(0, elapsedTicks) * MICROSECONDS_PER_TICK;
    }

    public static float subtitleAlpha(
            double gameTime,
            long cueStartGameTime,
            int durationTicks
    ) {
        if (durationTicks <= 0) {
            return 0.0F;
        }
        double elapsed = gameTime - cueStartGameTime;
        if (elapsed < 0.0D || elapsed >= durationTicks) {
            return 0.0F;
        }
        float fadeIn = Mth.clamp((float) (elapsed / 5.0D), 0.0F, 1.0F);
        float fadeOut = Mth.clamp((float) ((durationTicks - elapsed) / 10.0D), 0.0F, 1.0F);
        return Math.min(fadeIn, fadeOut);
    }
}
