package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterPhysicsProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterPhysicsProfileRegistry;
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

    // Per-body baselines are public integration profiles; server config still
    // supplies global buoyancy, drag, and safety-cap multipliers.
    static final WaterPhysicsProfile BOAT_PROFILE = WaterPhysicsProfileRegistry.BOAT;
    static final WaterPhysicsProfile ITEM_PROFILE = WaterPhysicsProfileRegistry.ITEM;
    static final WaterPhysicsProfile LIVING_PROFILE = WaterPhysicsProfileRegistry.LIVING;

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
            WaterPhysicsProfile profile,
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
            WaterPhysicsProfile profile,
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
            WaterPhysicsProfile profile,
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
            WaterPhysicsProfile profile,
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
            WaterPhysicsProfile profile,
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
                Math.max(MINIMUM_AREA, sizeY * sizeZ * profile.lateralAreaScale()),
                profile.lateralDragCoefficient() * tuning.dragScale(),
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
                Math.max(MINIMUM_AREA, sizeX * sizeY * profile.lateralAreaScale()),
                profile.lateralDragCoefficient() * tuning.dragScale(),
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

    /**
     * Computes hull-oriented drag, planing lift, and surface-entry slamming.
     *
     * <p>Vanilla watercraft expose only a yaw-oriented collision body, so pitch
     * and roll remain presentation state. Translation is server-authoritative:
     * longitudinal/lateral drag follows the hull, high-speed partial immersion
     * creates bounded dynamic lift, and downward entry produces a normal-aligned
     * impulse instead of passing through a crest before vanilla reacts.</p>
     */
    static Vec3 watercraftVelocityDelta(
            BuoyancySample sample,
            AABB bounds,
            Vec3 entityVelocity,
            float yawDegrees,
            double previousSubmergedFraction,
            WaterPhysicsProfile profile,
            int payloadUnits,
            double deltaSeconds,
            RuntimeTuning tuning
    ) {
        if (sample == null || !sample.touchingWater() || profile == null
                || !profile.rigidWatercraft()) {
            return Vec3.ZERO;
        }
        double fraction = clamp(sample.submergedFraction(), 0.0, 1.0);
        if (tuning == null || !tuning.enabled() || fraction <= 0.0
                || !(deltaSeconds > 0.0)
                || !finite(sample.current()) || !finite(entityVelocity)) {
            return Vec3.ZERO;
        }

        double sizeX = Math.max(0.0, bounds.getXsize());
        double sizeY = Math.max(0.0, bounds.getYsize());
        double sizeZ = Math.max(0.0, bounds.getZsize());
        double volume = sizeX * sizeY * sizeZ;
        double mass = Math.max(MINIMUM_MASS, profile.effectiveMass(volume, payloadUnits));
        double yawRadians = Math.toRadians(yawDegrees);
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Vec3 starboard = new Vec3(Math.cos(yawRadians), 0.0, Math.sin(yawRadians));

        Vec3 velocityPerSecond = entityVelocity.scale(TICKS_PER_SECOND);
        Vec3 relativeVelocity = sample.current().subtract(velocityPerSecond);
        double relativeForward = relativeVelocity.dot(forward);
        double relativeLateral = relativeVelocity.dot(starboard);
        double hullLength = Math.max(sizeX, sizeZ);
        double hullWidth = Math.min(sizeX, sizeZ);
        double forwardArea = Math.max(
                MINIMUM_AREA,
                sizeY * Math.max(hullWidth, 0.1) * profile.longitudinalAreaScale()
        );
        double lateralArea = Math.max(
                MINIMUM_AREA,
                sizeY * Math.max(hullLength, 0.1) * profile.lateralAreaScale()
        );
        double planingArea = Math.max(
                MINIMUM_AREA,
                sizeX * sizeZ * profile.verticalAreaScale()
        );

        double accelerationForward = dragAcceleration(
                relativeForward,
                forwardArea,
                profile.longitudinalDragCoefficient() * tuning.dragScale(),
                fraction,
                mass
        );
        double accelerationLateral = dragAcceleration(
                relativeLateral,
                lateralArea,
                profile.lateralDragCoefficient() * tuning.dragScale(),
                fraction,
                mass
        );
        double accelerationY = dragAcceleration(
                relativeVelocity.y,
                planingArea,
                profile.verticalDragCoefficient() * tuning.dragScale(),
                fraction,
                mass
        );

        double displacedVolume = volume
                * fraction
                * profile.displacedVolumeScale()
                * tuning.buoyancyScale();
        accelerationY += WATER_DENSITY_KG_PER_CUBIC_BLOCK
                * GRAVITY_BLOCKS_PER_SECOND_SQUARED
                * displacedVolume
                / mass;

        Vec3 surfaceNormal = safeUpwardNormal(sample.surfaceNormal());
        double forwardSpeed = Math.abs(velocityPerSecond.dot(forward));
        double planingWindow = fraction * (1.0 - fraction);
        double planingAcceleration = 0.5
                * WATER_DENSITY_KG_PER_CUBIC_BLOCK
                * forwardSpeed * forwardSpeed
                * planingArea
                * profile.planingLiftCoefficient()
                * tuning.planingScale()
                * planingWindow
                / mass;

        double entryFraction = Math.max(
                0.0,
                fraction - clamp(previousSubmergedFraction, 0.0, 1.0)
        );
        double entrySpeed = Math.max(0.0, -velocityPerSecond.y);
        double slamAcceleration = entrySpeed
                * (entryFraction / deltaSeconds)
                * profile.slammingCoefficient()
                * tuning.slammingScale();

        Vec3 acceleration = forward.scale(accelerationForward)
                .add(starboard.scale(accelerationLateral))
                .add(0.0, accelerationY, 0.0)
                .add(surfaceNormal.scale(planingAcceleration + slamAcceleration));
        Vec3 delta = acceleration.scale(deltaSeconds / TICKS_PER_SECOND);
        return clampMagnitude(
                delta,
                profile.maximumDeltaVelocityPerTick() * tuning.maximumDeltaScale()
        );
    }

    /** Computes bounded yaw acceleration from lateral hull slip. */
    static double watercraftYawAcceleration(
            BuoyancySample sample,
            AABB bounds,
            Vec3 entityVelocity,
            float yawDegrees,
            WaterPhysicsProfile profile,
            int payloadUnits,
            RuntimeTuning tuning
    ) {
        if (sample == null || !sample.touchingWater() || profile == null
                || !profile.rigidWatercraft() || tuning == null || !tuning.enabled()) {
            return 0.0;
        }
        double fraction = clamp(sample.submergedFraction(), 0.0, 1.0);
        if (fraction <= 0.0 || !finite(sample.current()) || !finite(entityVelocity)) {
            return 0.0;
        }
        double yawRadians = Math.toRadians(yawDegrees);
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Vec3 starboard = new Vec3(Math.cos(yawRadians), 0.0, Math.sin(yawRadians));
        Vec3 velocityPerSecond = entityVelocity.scale(TICKS_PER_SECOND);
        Vec3 relative = sample.current().subtract(velocityPerSecond);
        double lateralSlip = relative.dot(starboard);
        double forwardSpeed = Math.abs(velocityPerSecond.dot(forward));
        double sizeX = Math.max(0.1, bounds.getXsize());
        double sizeY = Math.max(0.1, bounds.getYsize());
        double sizeZ = Math.max(0.1, bounds.getZsize());
        double hullLength = Math.max(sizeX, sizeZ);
        double hullWidth = Math.min(sizeX, sizeZ);
        double volume = sizeX * sizeY * sizeZ;
        double mass = profile.effectiveMass(volume, payloadUnits);
        double momentOfInertia = Math.max(
                0.01,
                mass * (hullLength * hullLength + hullWidth * hullWidth) / 12.0
        );
        double lateralArea = sizeY * hullLength * profile.lateralAreaScale();
        double stabilizingForce = 0.5
                * WATER_DENSITY_KG_PER_CUBIC_BLOCK
                * lateralArea
                * profile.angularStabilityCoefficient()
                * tuning.angularResponseScale()
                * tuning.dragScale()
                * fraction
                * lateralSlip
                * Math.abs(lateralSlip)
                * Math.min(1.0, 0.25 + forwardSpeed * 0.10);
        double torque = stabilizingForce * hullLength * 0.25;
        return clamp(torque / momentOfInertia, -2.5, 2.5);
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

    private static Vec3 safeUpwardNormal(Vec3 value) {
        if (!finite(value) || value.lengthSqr() <= 1.0e-12) {
            return new Vec3(0.0, 1.0, 0.0);
        }
        Vec3 normalized = value.normalize();
        if (normalized.y < 0.20) {
            normalized = new Vec3(normalized.x, 0.20, normalized.z).normalize();
        }
        return normalized;
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
            double maximumDeltaScale,
            double planingScale,
            double slammingScale,
            double angularResponseScale
    ) {
        static final RuntimeTuning DEFAULT = new RuntimeTuning(
                true, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

        RuntimeTuning {
            buoyancyScale = clamp(buoyancyScale, 0.0, 2.0);
            dragScale = clamp(dragScale, 0.0, 2.0);
            maximumDeltaScale = clamp(maximumDeltaScale, 0.0, 2.0);
            planingScale = clamp(planingScale, 0.0, 2.0);
            slammingScale = clamp(slammingScale, 0.0, 2.0);
            angularResponseScale = clamp(angularResponseScale, 0.0, 2.0);
        }
    }
}
