package com.thunder.wildernessodysseyapi.WorldGen.jigsaw;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Simple weighted-selection helper used for jigsaw-based structure generation.
 * <p>
 * This abstraction allows mods to provide custom piece weighting without
 * reimplementing the selection algorithm every time.
 */
public final class CustomJigsawManager {
    private CustomJigsawManager() {
    }

    /**
     * Selects a random entry from the provided list using the supplied weight
     * function. Entries with higher weights have a greater chance of being
     * returned.
     *
     * @param entries       the pool of entries to pick from
     * @param weightFunc    function returning a non-negative weight for an entry
     * @param random        random source
     * @param <T>           entry type
     * @return a randomly selected entry
     */
    public static <T> T selectWeighted(List<T> entries, ToIntFunction<T> weightFunc, RandomSource random) {
        int total = 0;
        for (T entry : entries) {
            total += Math.max(0, weightFunc.applyAsInt(entry));
        }
        if (total <= 0) {
            return entries.get(random.nextInt(entries.size()));
        }
        int target = random.nextInt(total);
        for (T entry : entries) {
            target -= Math.max(0, weightFunc.applyAsInt(entry));
            if (target < 0) {
                return entry;
            }
        }
        return entries.get(0);
    }
}
