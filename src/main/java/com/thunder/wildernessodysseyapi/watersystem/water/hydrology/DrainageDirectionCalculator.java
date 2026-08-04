package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;

/**
 * Selects a deterministic eight-way downhill direction from local terrain.
 *
 * <p>Callers supply the center and edge/corner height samples from one chunk.
 * This deliberately avoids neighbor-chunk access, so partially generated or
 * unloaded terrain cannot trigger synchronous generation.</p>
 */
public final class DrainageDirectionCalculator {

    private static final double MINIMUM_DROP = 0.25;

    private DrainageDirectionCalculator() {
    }

    /**
     * Returns the lowest direction when it is measurably below the center.
     *
     * <p>The argument order follows the clockwise enum order after
     * {@link DrainageDirection#SINK}. Equal samples retain that stable order,
     * making the result deterministic across machines and reloads.</p>
     */
    public static DrainageDirection calculate(
            double center,
            double north,
            double northEast,
            double east,
            double southEast,
            double south,
            double southWest,
            double west,
            double northWest
    ) {
        double safeCenter = finiteOrZero(center);
        double[] heights = {
                north,
                northEast,
                east,
                southEast,
                south,
                southWest,
                west,
                northWest
        };
        DrainageDirection best = DrainageDirection.SINK;
        double bestHeight = safeCenter - MINIMUM_DROP;
        for (int index = 0; index < heights.length; index++) {
            double candidate = finiteOrZero(heights[index]);
            if (candidate < bestHeight) {
                bestHeight = candidate;
                best = DrainageDirection.fromId(index + 1);
            }
        }
        return best;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
