package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterPhysicsProfile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Integrates short-lived server watercraft state between physics ticks.
 *
 * <p>Entry slamming needs the previous submerged fraction, while yaw stability
 * needs angular momentum instead of snapping the craft to a direction. Weak
 * keys let removed entities disappear without retaining world references.</p>
 */
final class WatercraftDynamicsState {

    private static final double YAW_DAMPING = 0.86;
    private static final double MAX_YAW_VELOCITY_DEGREES_PER_TICK = 1.6;
    private static final Map<Entity, State> STATES = new WeakHashMap<>();

    private WatercraftDynamicsState() {
    }

    /** Advances one rigid-watercraft response on the logical server. */
    static synchronized Response update(
            Entity entity,
            BuoyancySample buoyancy,
            WaterPhysicsProfile profile,
            int payloadUnits,
            HydrodynamicForces.RuntimeTuning tuning
    ) {
        State state = STATES.computeIfAbsent(entity, ignored -> new State());
        Vec3 velocityDelta = HydrodynamicForces.watercraftVelocityDelta(
                buoyancy,
                entity.getBoundingBox(),
                entity.getDeltaMovement(),
                entity.getYRot(),
                state.previousSubmergedFraction,
                profile,
                payloadUnits,
                HydrodynamicForces.FIXED_DELTA_SECONDS,
                tuning
        );
        double yawAcceleration = HydrodynamicForces.watercraftYawAcceleration(
                buoyancy,
                entity.getBoundingBox(),
                entity.getDeltaMovement(),
                entity.getYRot(),
                profile,
                payloadUnits,
                tuning
        );
        state.yawVelocityDegreesPerTick = integrateYawVelocity(
                state.yawVelocityDegreesPerTick,
                yawAcceleration,
                HydrodynamicForces.FIXED_DELTA_SECONDS
        );
        state.previousSubmergedFraction = buoyancy.submergedFraction();
        return new Response(velocityDelta, state.yawVelocityDegreesPerTick);
    }

    /** Removes stale momentum as soon as a craft is no longer water-supported. */
    static synchronized void leaveWater(Entity entity) {
        STATES.remove(entity);
    }

    /** Clears all transient server state during shutdown or test teardown. */
    static synchronized void clear() {
        STATES.clear();
    }

    static double integrateYawVelocity(
            double previousDegreesPerTick,
            double yawAccelerationRadiansPerSecondSquared,
            double deltaSeconds
    ) {
        if (!Double.isFinite(previousDegreesPerTick)
                || !Double.isFinite(yawAccelerationRadiansPerSecondSquared)
                || !(deltaSeconds > 0.0)) {
            return 0.0;
        }
        double accelerationDeltaDegreesPerTick = Math.toDegrees(
                yawAccelerationRadiansPerSecondSquared * deltaSeconds
        ) / 20.0;
        double next = (previousDegreesPerTick + accelerationDeltaDegreesPerTick) * YAW_DAMPING;
        return Math.max(
                -MAX_YAW_VELOCITY_DEGREES_PER_TICK,
                Math.min(MAX_YAW_VELOCITY_DEGREES_PER_TICK, next)
        );
    }

    /** Complete server movement response for one watercraft tick. */
    record Response(Vec3 velocityDelta, double yawDegreesPerTick) {
    }

    private static final class State {
        private double previousSubmergedFraction;
        private double yawVelocityDegreesPerTick;
    }
}
