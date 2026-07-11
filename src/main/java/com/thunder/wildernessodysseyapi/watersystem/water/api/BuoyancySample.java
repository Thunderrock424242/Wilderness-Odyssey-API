package com.thunder.wildernessodysseyapi.watersystem.water.api;

import net.minecraft.world.phys.Vec3;

/**
 * Reusable physics description of an object's relationship with a water surface.
 *
 * @param touchingWater whether any vertical portion intersects custom water
 * @param submerged whether the entire bounds are below the sampled surface
 * @param surfaceHeight animated surface height at the bounds center
 * @param submergedFraction fraction of bounds height below the surface
 * @param current authority current at the bounds center
 * @param surfaceNormal analytic wave-surface normal
 */
public record BuoyancySample(
        boolean touchingWater,
        boolean submerged,
        double surfaceHeight,
        double submergedFraction,
        Vec3 current,
        Vec3 surfaceNormal
) {

    /** Shared dry result avoids allocations when no custom water is present. */
    public static final BuoyancySample DRY = new BuoyancySample(
            false,
            false,
            Double.NaN,
            0.0,
            Vec3.ZERO,
            new Vec3(0.0, 1.0, 0.0)
    );
}
