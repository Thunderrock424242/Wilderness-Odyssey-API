package com.thunder.wildernessodysseyapi.watersystem.water.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Public query and controlled-mutation boundary for custom Wilderness water.
 *
 * <p>Callers must not mutate canonical chunks, projection blocks, large-body
 * caches, or SPH storage directly. Mutations are accepted only on the logical
 * server; clients may use this service for rendering and prediction queries.</p>
 */
public interface WaterAccess {

    /** Returns whether authority-owned water occupies this block. */
    boolean isWaterAt(Level level, BlockPos position);

    /** Returns whether authority-owned water occupies this world position. */
    boolean isWaterAt(Level level, Vec3 position);

    /**
     * Returns authority units at this block, or zero when Wilderness does not own it.
     *
     * <p>The default keeps third-party {@code WaterAccess} implementations
     * binary-compatible; implementations that expose mutable volume should
     * override it with their authoritative read.</p>
     */
    default long getWaterUnits(Level level, BlockPos position) {
        return 0L;
    }

    /** Returns whether the bounds intersect the custom surface at their center. */
    boolean isSubmerged(Level level, AABB bounds);

    /** Returns animated surface Y, or {@link Double#NaN} for a dry column. */
    double getSurfaceHeight(Level level, double x, double z);

    /** Returns vertical depth above a point inside custom water. */
    double getDepth(Level level, Vec3 position);

    /** Returns current velocity at a position, or {@link Vec3#ZERO} when dry. */
    Vec3 getCurrent(Level level, Vec3 position);

    /** Returns read-only metadata for the authority body occupying a position. */
    Optional<WaterBody> getWaterBody(Level level, BlockPos position);

    /**
     * Returns authoritative chunk-scale river and watershed conditions.
     *
     * <p>The default preserves binary compatibility for optional third-party
     * implementations that have not adopted watershed metadata.</p>
     */
    default WatershedConditions getWatershedConditions(Level level, BlockPos position) {
        return WatershedConditions.NONE;
    }

    /** Returns compact within-chunk tributary flow without exposing storage internals. */
    default WatershedLocalFlow getLocalWatershedFlow(Level level, BlockPos position) {
        return WatershedLocalFlow.NONE;
    }

    /** Returns whether the loaded server position can accept local volume. */
    boolean canAddWater(Level level, BlockPos position);

    /** Fills a caller-owned sample for allocation-sensitive query paths. */
    void sample(Level level, double x, double y, double z, float partialTick, WaterSample result);

    /** Adds authority units on the logical server. */
    default WaterInteractionResult addWater(Level level, BlockPos position, long amountUnits) {
        return addWater(level, position, amountUnits, false);
    }

    /** Adds or simulates an authority-unit transfer on the logical server. */
    WaterInteractionResult addWater(
            Level level,
            BlockPos position,
            long amountUnits,
            boolean simulate
    );

    /** Removes authority units on the logical server. */
    default WaterInteractionResult removeWater(Level level, BlockPos position, long amountUnits) {
        return removeWater(level, position, amountUnits, false);
    }

    /** Removes or simulates an authority-unit transfer on the logical server. */
    WaterInteractionResult removeWater(
            Level level,
            BlockPos position,
            long amountUnits,
            boolean simulate
    );
}
