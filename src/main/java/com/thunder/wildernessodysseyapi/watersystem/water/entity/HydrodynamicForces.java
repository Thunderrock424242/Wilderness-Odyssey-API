package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Converts authority samples into bounded mass-aware entity velocity changes.
 *
 * <p>Water currents are expressed in blocks per second while Minecraft entity
 * velocity is stored in blocks per tick. This helper performs that conversion,
 * integrates force over an explicit timestep, and caps extreme impulses so a
 * transient simulation spike cannot launch an entity.</p>
 */
final class HydrodynamicForces {

    static final double FIXED_DELTA_SECONDS = 1.0 / 20.0;
    static final double SHORELINE_CURRENT_SCALE = 0.65;
    static final double MOBILE_CURRENT_SCALE = 0.20;
    static final double MOBILE_DRAG_FRACTION = 0.20;

    // Per-body baselines stay centralized; server config supplies the global
    // buoyancy, drag, and safety-cap multipliers through RuntimeTuning.
    static final ForceProfile BOAT_PROFILE = new ForceProfile(
            280.0, 180.0, 0.0,
            0.62,
            0.55, 0.25,
            0.70, 0.75,
            0.032
    );
    static final ForceProfile ITEM_PROFILE = new ForceProfile(
            0.25, 1.0, 0.025,
            0.040,
            0.18, 0.08,
            0.55, 0.40,
            0.018
    );
    static final ForceProfile LIVING_PROFILE = new ForceProfile(
            48.0, 30.0, 0.0,
            0.0,
            0.040, 0.008,
            0.50, 0.20,
            0.006
    );

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double WATER_DENSITY_KG_PER_CUBIC_BLOCK = 1_000.0;
    private static final double GRAVITY_BLOCKS_PER_SECOND_SQUARED = 9.81;
    private static final double MINIMUM_AREA = 0.01;
    private static final double MINIMUM_MASS = 0.01;

    private HydrodynamicForces() {
    }

    static Vec3 velocityDelta(
            BuoyancySample sample,
            AABB bounds,
            Vec3 entityVelocity,
            ForceProfile profile,
            int payloadUnits,
            double deltaSeconds
    ) {
        return velocityDelta(
                sample,
                bounds,
                entityVelocity,
                profile,
                payloadUnits,
                deltaSeconds,
                RuntimeTuning.DEFAULT
        );
    }

    static Vec3 velocityDelta(
            BuoyancySample sample,
            AABB bounds,
            Vec3 entityVelocity,
            ForceProfile profile,
            int payloadUnits,
            double deltaSeconds,
            RuntimeTuning tuning
    ) {
        if (sample == null || !sample.touchingWater()) {
            return Vec3.ZERO;
        }
        return velocityDelta(
                sample.current(),
                bounds,
                entityVelocity,
                sample.submergedFraction(),
                profile,
                payloadUnits,
                deltaSeconds,
                true,
                tuning
        );
    }

    static Vec3 dragOnlyVelocityDelta(
            Vec3 current,
            AABB bounds,
            Vec3 entityVelocity,
            double submergedFraction,
            ForceProfile profile,
            int payloadUnits,
            double deltaSeconds
    ) {
        return dragOnlyVelocityDelta(
                current,
                bounds,
                entityVelocity,
                submergedFraction,
                profile,
                payloadUnits,
                deltaSeconds,
                RuntimeTuning.DEFAULT
        );
    }

    static Vec3 dragOnlyVelocityDelta(
            Vec3 current,
            AABB bounds,
            Vec3 entityVelocity,
            double submergedFraction,
            ForceProfile profile,
            int payloadUnits,
            double deltaSeconds,
            RuntimeTuning tuning
    ) {
        return velocityDelta(
                current,
                bounds,
                entityVelocity,
                submergedFraction,
                profile,
                payloadUnits,
                deltaSeconds,
                false,
                tuning
        );
    }

    private static Vec3 velocityDelta(
            Vec3 current,
            AABB bounds,
            Vec3 entityVelocity,
            double submergedFraction,
            ForceProfile profile,
            int payloadUnits,
            double deltaSeconds,
            boolean includeBuoyancy,
            RuntimeTuning tuning
    ) {
        double fraction = clamp(submergedFraction, 0.0, 1.0);
        if (tuning == null || !tuning.enabled()
                || fraction <= 0.0 || !(deltaSeconds > 0.0)
                || !finite(current) || !finite(entityVelocity)) {
            return Vec3.ZERO;
        }

        double sizeX = Math.max(0.0, bounds.getXsize());
        double sizeY = Math.max(0.0, bounds.getYsize());
        double sizeZ = Math.max(0.0, bounds.getZsize());
        double volume = sizeX * sizeY * sizeZ;
        double mass = Math.max(MINIMUM_MASS, profile.effectiveMass(volume, payloadUnits));

        // Drag reacts to fluid-relative speed, not absolute entity motion. Each
        // axis uses the projected wetted area normal to that direction.
        Vec3 velocityPerSecond = entityVelocity.scale(TICKS_PER_SECOND);
        Vec3 relativeVelocity = current.subtract(velocityPerSecond);
        double accelerationX = dragAcceleration(
                relativeVelocity.x,
                Math.max(MINIMUM_AREA, sizeY * sizeZ * profile.horizontalAreaScale()),
                profile.horizontalDragCoefficient() * tuning.dragScale(),
                fraction,
                mass
        );
        double accelerationY = dragAcceleration(
                relativeVelocity.y,
                Math.max(MINIMUM_AREA, sizeX * sizeZ * profile.verticalAreaScale()),
                profile.verticalDragCoefficient() * tuning.dragScale(),
                fraction,
                mass
        );
        double accelerationZ = dragAcceleration(
                relativeVelocity.z,
                Math.max(MINIMUM_AREA, sizeX * sizeY * profile.horizontalAreaScale()),
                profile.horizontalDragCoefficient() * tuning.dragScale(),
                fraction,
                mass
        );

        if (includeBuoyancy && profile.displacedVolumeScale() > 0.0) {
            double displacedVolume = volume
                    * fraction
                    * profile.displacedVolumeScale()
                    * tuning.buoyancyScale();
            accelerationY += WATER_DENSITY_KG_PER_CUBIC_BLOCK
                    * GRAVITY_BLOCKS_PER_SECOND_SQUARED
                    * displacedVolume
                    / mass;
        }

        double accelerationToTickVelocity = deltaSeconds / TICKS_PER_SECOND;
        Vec3 delta = new Vec3(accelerationX, accelerationY, accelerationZ)
                .scale(accelerationToTickVelocity);
        return clampMagnitude(
                delta,
                profile.maximumDeltaVelocityPerTick() * tuning.maximumDeltaScale()
        );
    }

    private static double dragAcceleration(
            double relativeVelocity,
            double wettedArea,
            double dragCoefficient,
            double submergedFraction,
            double mass
    ) {
        return 0.5
                * WATER_DENSITY_KG_PER_CUBIC_BLOCK
                * Math.max(0.0, dragCoefficient)
                * wettedArea
                * submergedFraction
                * relativeVelocity
                * Math.abs(relativeVelocity)
                / mass;
    }

    private static Vec3 clampMagnitude(Vec3 value, double maximumMagnitude) {
        double maximum = Math.max(0.0, maximumMagnitude);
        double lengthSquared = value.lengthSqr();
        if (lengthSquared <= maximum * maximum || lengthSquared <= 1.0e-18) {
            return value;
        }
        return value.scale(maximum / Math.sqrt(lengthSquared));
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record ForceProfile(
            double baseMassKg,
            double massPerCubicBlockKg,
            double massPerPayloadUnitKg,
            double displacedVolumeScale,
            double horizontalDragCoefficient,
            double verticalDragCoefficient,
            double horizontalAreaScale,
            double verticalAreaScale,
            double maximumDeltaVelocityPerTick
    ) {
        double effectiveMass(double volume, int payloadUnits) {
            return baseMassKg
                    + Math.max(0.0, volume) * massPerCubicBlockKg
                    + Math.max(0, payloadUnits) * massPerPayloadUnitKg;
        }
    }

    /**
     * Global runtime multipliers intentionally kept separate from body profiles.
     *
     * <p>This is the single seam for server config without spreading config
     * reads through the force math or per-entity handlers.</p>
     */
    record RuntimeTuning(
            boolean enabled,
            double buoyancyScale,
            double dragScale,
            double maximumDeltaScale
    ) {
        static final RuntimeTuning DEFAULT = new RuntimeTuning(true, 1.0, 1.0, 1.0);

        RuntimeTuning {
            buoyancyScale = clamp(buoyancyScale, 0.0, 2.0);
            dragScale = clamp(dragScale, 0.0, 2.0);
            maximumDeltaScale = clamp(maximumDeltaScale, 0.0, 2.0);
        }
    }
}
