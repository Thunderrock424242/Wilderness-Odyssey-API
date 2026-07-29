package com.thunder.wildernessodysseyapi.watersystem.water.authority;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import net.minecraft.world.phys.Vec3;

/**
 * Aggregates footprint samples into one displacement-weighted physics sample.
 *
 * <p>Dry footprint points still count toward the average submerged fraction,
 * while current and normal values are weighted by displaced water. This keeps
 * a hull entering a crest from receiving the force of a fully submerged hull.</p>
 */
final class BuoyancySampleAccumulator {

    private static final double FULLY_SUBMERGED_EPSILON = 1.0e-6;

    private int pointCount;
    private int wetPointCount;
    private double submergedFractionSum;
    private double weightedSurfaceHeight;
    private double weightedCurrentX;
    private double weightedCurrentY;
    private double weightedCurrentZ;
    private double weightedNormalX;
    private double weightedNormalY;
    private double weightedNormalZ;
    private boolean everyWetPointFullySubmerged;

    BuoyancySampleAccumulator reset() {
        pointCount = 0;
        wetPointCount = 0;
        submergedFractionSum = 0.0;
        weightedSurfaceHeight = 0.0;
        weightedCurrentX = 0.0;
        weightedCurrentY = 0.0;
        weightedCurrentZ = 0.0;
        weightedNormalX = 0.0;
        weightedNormalY = 0.0;
        weightedNormalZ = 0.0;
        everyWetPointFullySubmerged = true;
        return this;
    }

    void add(WaterSample water, double minimumY, double maximumY) {
        pointCount++;
        if (!water.water()) {
            everyWetPointFullySubmerged = false;
            return;
        }

        double fraction = AuthorityWaterBuoyancyProvider.submergedFraction(
                minimumY,
                maximumY,
                water.surfaceHeight()
        );
        if (fraction <= 0.0) {
            everyWetPointFullySubmerged = false;
            return;
        }

        wetPointCount++;
        submergedFractionSum += fraction;
        weightedSurfaceHeight += water.surfaceHeight() * fraction;
        weightedCurrentX += water.currentX() * fraction;
        weightedCurrentY += water.currentY() * fraction;
        weightedCurrentZ += water.currentZ() * fraction;
        weightedNormalX += water.normalX() * fraction;
        weightedNormalY += water.normalY() * fraction;
        weightedNormalZ += water.normalZ() * fraction;
        everyWetPointFullySubmerged &= fraction >= 1.0 - FULLY_SUBMERGED_EPSILON;
    }

    BuoyancySample finish() {
        if (pointCount <= 0 || wetPointCount <= 0 || submergedFractionSum <= 0.0) {
            return BuoyancySample.DRY;
        }

        double inverseWeight = 1.0 / submergedFractionSum;
        Vec3 normal = new Vec3(
                weightedNormalX * inverseWeight,
                weightedNormalY * inverseWeight,
                weightedNormalZ * inverseWeight
        );
        if (normal.lengthSqr() <= 1.0e-12) {
            normal = new Vec3(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }

        return new BuoyancySample(
                true,
                wetPointCount == pointCount && everyWetPointFullySubmerged,
                weightedSurfaceHeight * inverseWeight,
                Math.min(1.0, submergedFractionSum / pointCount),
                new Vec3(
                        weightedCurrentX * inverseWeight,
                        weightedCurrentY * inverseWeight,
                        weightedCurrentZ * inverseWeight
                ),
                normal
        );
    }
}
