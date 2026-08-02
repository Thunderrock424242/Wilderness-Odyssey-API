package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;

import java.util.Locale;

/**
 * Converts synchronized tide samples into stable, localizable client text.
 *
 * <p>This model contains no renderer or item behavior, which keeps clock text
 * formatting deterministic and independently testable.</p>
 */
final class TideClockDisplayModel {

    private static final float TURNING_RATE_THRESHOLD = 0.001f;
    private static final String[] MOON_TRANSLATION_KEYS = {
            "tide.wildernessodysseyapi.moon.full",
            "tide.wildernessodysseyapi.moon.waning_gibbous",
            "tide.wildernessodysseyapi.moon.last_quarter",
            "tide.wildernessodysseyapi.moon.waning_crescent",
            "tide.wildernessodysseyapi.moon.new",
            "tide.wildernessodysseyapi.moon.waxing_crescent",
            "tide.wildernessodysseyapi.moon.first_quarter",
            "tide.wildernessodysseyapi.moon.waxing_gibbous"
    };

    private TideClockDisplayModel() {
    }

    /** Builds the compact strings and translation keys shown by a vanilla clock. */
    static TideReadout create(TideSystem.TideSample sample, String tideName) {
        return new TideReadout(
                tideName,
                formatOffset(sample.offset()),
                trendTranslationKey(sample.rate()),
                moonTranslationKey(sample.moonPhase())
        );
    }

    /** Shows contextual tide information only when a real vanilla clock is in focus. */
    static boolean shouldShowContextualDisplay(
            boolean mainHandClock,
            boolean offHandClock,
            boolean targetedFramedClock
    ) {
        return mainHandClock || offHandClock || targetedFramedClock;
    }

    private static String formatOffset(float offset) {
        // Avoid displaying "-0.00" while the tide crosses mean sea level.
        float displayOffset = Math.abs(offset) < 0.005f ? 0.0f : offset;
        return String.format(Locale.ROOT, "%+.2f", displayOffset);
    }

    private static String trendTranslationKey(float rate) {
        if (rate > TURNING_RATE_THRESHOLD) {
            return "tide.wildernessodysseyapi.trend.rising";
        }
        if (rate < -TURNING_RATE_THRESHOLD) {
            return "tide.wildernessodysseyapi.trend.falling";
        }
        return "tide.wildernessodysseyapi.trend.turning";
    }

    private static String moonTranslationKey(int moonPhase) {
        return MOON_TRANSLATION_KEYS[Math.floorMod(moonPhase, MOON_TRANSLATION_KEYS.length)];
    }

    /** Immutable text model shared by tooltip and held-clock rendering. */
    record TideReadout(
            String tideName,
            String offsetBlocks,
            String trendTranslationKey,
            String moonTranslationKey
    ) {
    }
}
