package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterUnits;

/**
 * Converts NeoForge millibuckets to the fixed-point units owned by Wilderness water.
 *
 * <p>Conversions use the represented absolute tank level instead of rounding
 * each transfer independently. This preserves volume across repeated one
 * millibucket operations even though one block contains 4,096 authority units
 * and 1,000 millibuckets.</p>
 */
public final class WaterUnitConversions {

    /** NeoForge amount represented by one full world-fluid block. */
    public static final int MILLIBUCKETS_PER_BLOCK = 1_000;

    private WaterUnitConversions() {
    }

    /** Returns the whole millibuckets represented by an authority-unit amount. */
    public static int toMilliBuckets(long amountUnits) {
        long bounded = Math.max(0L, Math.min(WaterUnits.UNITS_PER_BLOCK, amountUnits));
        return (int) (bounded * MILLIBUCKETS_PER_BLOCK / WaterUnits.UNITS_PER_BLOCK);
    }

    /** Returns the lowest authority-unit value that represents this visible tank level. */
    public static long targetUnitsForMilliBuckets(int milliBuckets) {
        int bounded = Math.max(0, Math.min(MILLIBUCKETS_PER_BLOCK, milliBuckets));
        if (bounded == 0) {
            return 0L;
        }
        return ((long) bounded * WaterUnits.UNITS_PER_BLOCK
                + MILLIBUCKETS_PER_BLOCK - 1L) / MILLIBUCKETS_PER_BLOCK;
    }

    /** Plans a bounded fill against the current absolute authority level. */
    public static TransferPlan planFill(long currentUnits, int requestedMilliBuckets) {
        long boundedUnits = boundedUnits(currentUnits);
        int currentMilliBuckets = toMilliBuckets(boundedUnits);
        long residualUnits = boundedUnits - targetUnitsForMilliBuckets(currentMilliBuckets);
        int requested = Math.min(
                Math.max(0, requestedMilliBuckets),
                MILLIBUCKETS_PER_BLOCK - currentMilliBuckets
        );
        return findPlan(boundedUnits, currentMilliBuckets, residualUnits, requested, true);
    }

    /** Plans a bounded drain against the current absolute authority level. */
    public static TransferPlan planDrain(long currentUnits, int requestedMilliBuckets) {
        long boundedUnits = boundedUnits(currentUnits);
        int currentMilliBuckets = toMilliBuckets(boundedUnits);
        long residualUnits = boundedUnits - targetUnitsForMilliBuckets(currentMilliBuckets);
        int requested = Math.min(Math.max(0, requestedMilliBuckets), currentMilliBuckets);
        return findPlan(boundedUnits, currentMilliBuckets, residualUnits, requested, false);
    }

    private static long boundedUnits(long currentUnits) {
        return Math.max(0L, Math.min(WaterUnits.UNITS_PER_BLOCK, currentUnits));
    }

    /*
     * One visible millibucket bin contains either four or five authority-unit
     * values. Carry the cell's offset within its current bin into the target
     * bin. If that target bin is narrower, try a smaller visible transfer rather
     * than silently discarding the hidden units.
     */
    private static TransferPlan findPlan(
            long currentUnits,
            int currentMilliBuckets,
            long residualUnits,
            int requestedMilliBuckets,
            boolean filling
    ) {
        for (int transferred = requestedMilliBuckets; transferred > 0; transferred--) {
            int targetMilliBuckets = filling
                    ? currentMilliBuckets + transferred
                    : currentMilliBuckets - transferred;
            long targetUnits = targetUnitsForMilliBuckets(targetMilliBuckets) + residualUnits;
            if (targetUnits < 0L
                    || targetUnits > WaterUnits.UNITS_PER_BLOCK
                    || toMilliBuckets(targetUnits) != targetMilliBuckets) {
                continue;
            }
            return new TransferPlan(
                    transferred,
                    targetUnits,
                    Math.abs(targetUnits - currentUnits)
            );
        }
        return new TransferPlan(0, currentUnits, 0L);
    }

    /**
     * Immutable absolute-level transaction plan.
     *
     * @param milliBuckets amount reported through NeoForge
     * @param targetUnits authority level after a complete transfer
     * @param deltaUnits authority units added or removed by the transfer
     */
    public record TransferPlan(int milliBuckets, long targetUnits, long deltaUnits) {
    }
}
