package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Produces the bounded footprint sample used by {@link EntityWaterCompat}.
 *
 * <p>Four inset corners prevent a boat bow, large mob, or item at a shoreline
 * from being classified as dry merely because its center is over land. The
 * fifth point is biased into motion so fast entities see an entering crest at
 * their leading edge. All six queries, including the independent eye query,
 * are folded into the entity's once-per-tick cache.</p>
 */
final class EntityWaterContactSampler {

    private static final double MIN_SAMPLE_INSET = 0.02;
    private static final double MAX_SAMPLE_INSET = 0.18;
    private static final double LEADING_SAMPLE_BIAS = 0.35;
    private static final double SURFACE_RELEVANCE_MARGIN = 1.0;
    private static final double FULLY_SUBMERGED_EPSILON = 1.0e-6;

    private EntityWaterContactSampler() {
    }

    /** Samples body footprint and eye height, then updates the supplied cache. */
    static void sample(WaterAccess access, Entity entity, EntityWaterState state) {
        AABB bounds = entity.getBoundingBox();
        Footprint footprint = footprint(bounds, entity.getDeltaMovement());
        ContactAccumulator accumulator = state.contactAccumulator().reset(bounds);
        WaterSample query = state.queryScratch();
        double queryY = Math.nextUp(bounds.minY);

        // The corner samples detect edge contact; the leading point prevents a
        // moving bow from waiting for the entity center to cross the shoreline.
        sampleBody(access, entity, query, accumulator, footprint.minimumX(), queryY, footprint.minimumZ());
        sampleBody(access, entity, query, accumulator, footprint.maximumX(), queryY, footprint.minimumZ());
        sampleBody(access, entity, query, accumulator, footprint.minimumX(), queryY, footprint.maximumZ());
        sampleBody(access, entity, query, accumulator, footprint.maximumX(), queryY, footprint.maximumZ());
        sampleBody(access, entity, query, accumulator, footprint.leadingX(), queryY, footprint.leadingZ());

        // Eye state is a point query, not a hull average. This lets animated
        // troughs uncover a player's eyes without changing ordinary tagged water.
        access.sample(entity.level(), entity.getX(), entity.getEyeY(), entity.getZ(), 0.0f, query);
        accumulator.addEye(query, entity.getEyeY());
        state.update(
                entity,
                accumulator.authoritativeContactKnown(),
                accumulator.touchingWater(),
                accumulator.bodySubmerged(),
                accumulator.eyesSubmerged(),
                accumulator.surfaceHeight(),
                accumulator.depth(),
                accumulator.currentX(),
                accumulator.currentY(),
                accumulator.currentZ()
        );
    }

    private static void sampleBody(
            WaterAccess access,
            Entity entity,
            WaterSample query,
            ContactAccumulator accumulator,
            double x,
            double y,
            double z
    ) {
        access.sample(entity.level(), x, y, z, 0.0f, query);
        accumulator.addBody(query);
    }

    /** Returns deterministic corner and motion-leading coordinates for bounds. */
    static Footprint footprint(AABB bounds, Vec3 velocity) {
        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        double halfSampleX = sampleHalfExtent(bounds.getXsize());
        double halfSampleZ = sampleHalfExtent(bounds.getZsize());
        Vec3 motion = velocity == null ? Vec3.ZERO : velocity;
        double leadX = clamp(motion.x * LEADING_SAMPLE_BIAS, -halfSampleX, halfSampleX);
        double leadZ = clamp(motion.z * LEADING_SAMPLE_BIAS, -halfSampleZ, halfSampleZ);
        return new Footprint(
                centerX - halfSampleX,
                centerX + halfSampleX,
                centerZ - halfSampleZ,
                centerZ + halfSampleZ,
                centerX + leadX,
                centerZ + leadZ
        );
    }

    private static double sampleHalfExtent(double size) {
        double inset = clamp(size * 0.15, MIN_SAMPLE_INSET, MAX_SAMPLE_INSET);
        return Math.max(0.0, size * 0.5 - inset);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Immutable sample coordinates retained separately from water state. */
    record Footprint(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double leadingX,
            double leadingZ
    ) {
    }

    /** Allocation-free reduction of the five body samples and one eye sample. */
    static final class ContactAccumulator {

        private AABB bounds;
        private int pointCount;
        private int wetPointCount;
        private double submergedWeight;
        private double weightedSurfaceHeight;
        private double weightedDepth;
        private double weightedCurrentX;
        private double weightedCurrentY;
        private double weightedCurrentZ;
        private double nearbySurfaceHeight;
        private boolean everyPointFullySubmerged;
        private boolean authoritativeContactKnown;
        private boolean eyesSubmerged;

        ContactAccumulator reset(AABB bounds) {
            this.bounds = bounds;
            pointCount = 0;
            wetPointCount = 0;
            submergedWeight = 0.0;
            weightedSurfaceHeight = 0.0;
            weightedDepth = 0.0;
            weightedCurrentX = 0.0;
            weightedCurrentY = 0.0;
            weightedCurrentZ = 0.0;
            nearbySurfaceHeight = Double.NaN;
            everyPointFullySubmerged = true;
            authoritativeContactKnown = false;
            eyesSubmerged = false;
            return this;
        }

        void addBody(WaterSample water) {
            pointCount++;
            observeSurface(water.surfaceHeight());
            double fraction = submergedFraction(bounds.minY, bounds.maxY, water.surfaceHeight());
            boolean nearAnimatedSurface = !Double.isNaN(water.surfaceHeight())
                    && Math.abs(water.surfaceHeight() - bounds.minY) <= SURFACE_RELEVANCE_MARGIN;
            authoritativeContactKnown |= water.water() || nearAnimatedSurface;
            boolean crestContact = nearAnimatedSurface && bounds.minY <= water.surfaceHeight();
            if ((!water.water() && !crestContact) || fraction <= 0.0) {
                everyPointFullySubmerged = false;
                return;
            }

            wetPointCount++;
            submergedWeight += fraction;
            weightedSurfaceHeight += water.surfaceHeight() * fraction;
            weightedDepth += Math.max(water.depth(), water.surfaceHeight() - bounds.minY) * fraction;
            weightedCurrentX += water.currentX() * fraction;
            weightedCurrentY += water.currentY() * fraction;
            weightedCurrentZ += water.currentZ() * fraction;
            everyPointFullySubmerged &= fraction >= 1.0 - FULLY_SUBMERGED_EPSILON;
        }

        void addEye(WaterSample water, double eyeY) {
            observeSurface(water.surfaceHeight());
            authoritativeContactKnown |= water.water();
            // The eye can sit in the animated crest above the highest physical
            // fluid block. Require body contact before extending into that shell
            // so a dry cave below an ocean column is never treated as flooded.
            eyesSubmerged = water.water()
                    || touchingWater()
                    && !Double.isNaN(water.surfaceHeight())
                    && eyeY <= water.surfaceHeight();
        }

        private void observeSurface(double surfaceHeight) {
            if (Double.isNaN(surfaceHeight)) {
                return;
            }
            nearbySurfaceHeight = Double.isNaN(nearbySurfaceHeight)
                    ? surfaceHeight
                    : Math.max(nearbySurfaceHeight, surfaceHeight);
        }

        private static double submergedFraction(double minimumY, double maximumY, double surfaceHeight) {
            double height = maximumY - minimumY;
            if (height <= 0.0 || Double.isNaN(surfaceHeight)) {
                return 0.0;
            }
            return clamp((surfaceHeight - minimumY) / height, 0.0, 1.0);
        }

        boolean authoritativeContactKnown() {
            return authoritativeContactKnown;
        }

        boolean touchingWater() {
            return wetPointCount > 0;
        }

        boolean bodySubmerged() {
            return pointCount > 0 && wetPointCount == pointCount && everyPointFullySubmerged;
        }

        boolean eyesSubmerged() {
            return eyesSubmerged;
        }

        double surfaceHeight() {
            return submergedWeight > 0.0
                    ? weightedSurfaceHeight / submergedWeight
                    : nearbySurfaceHeight;
        }

        double depth() {
            return submergedWeight > 0.0 ? weightedDepth / submergedWeight : 0.0;
        }

        double currentX() {
            return submergedWeight > 0.0 ? weightedCurrentX / submergedWeight : 0.0;
        }

        double currentY() {
            return submergedWeight > 0.0 ? weightedCurrentY / submergedWeight : 0.0;
        }

        double currentZ() {
            return submergedWeight > 0.0 ? weightedCurrentZ / submergedWeight : 0.0;
        }
    }
}
