package com.thunder.wildernessodysseyapi.vegetation.simulation;

/** Pure per-tick cap used to drain due loaded chunks without a burst. */
public final class VegetationWorkBudget {

    private VegetationWorkBudget() {
    }

    /**
     * Returns the maximum due chunks processed in one level tick.
     *
     * <p>The expected average is loaded chunks divided by interval, with one
     * spare slot for hash collisions and short scheduling backlogs.</p>
     */
    public static int maximumChunksPerTick(int loadedChunks, int updateIntervalTicks) {
        if (loadedChunks <= 0) {
            return 0;
        }
        int interval = Math.max(1, updateIntervalTicks);
        return Math.min(loadedChunks, Math.max(1, (loadedChunks + interval - 1) / interval + 1));
    }
}
