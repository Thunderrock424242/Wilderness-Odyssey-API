package com.thunder.wildernessodysseyapi.environment.glacial.runtime;

/** Pure budget calculations for bounded loaded-chunk glacier work. */
public final class GlacialWorkBudget {

    private GlacialWorkBudget() {
    }

    /** Returns the number of surface samples allowed for one level tick. */
    public static int samplesPerTick(int configuredBudget, int loadedGlacialChunks) {
        if (configuredBudget <= 0 || loadedGlacialChunks <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(configuredBudget, loadedGlacialChunks * 4));
    }
}
