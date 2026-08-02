package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterPhysicsProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers force direction, mass scaling, and timestep integration. */
class HydrodynamicForcesTest {

    private static final AABB UNIT_BOUNDS = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    @Test
    void drySamplesNeverAlterMovement() {
        Vec3 delta = HydrodynamicForces.velocityDelta(
                BuoyancySample.DRY,
                UNIT_BOUNDS,
                new Vec3(0.3, -0.2, 0.1),
                HydrodynamicForces.BOAT_PROFILE,
                0,
                HydrodynamicForces.FIXED_DELTA_SECONDS
        );

        assertEquals(Vec3.ZERO, delta);
    }

    @Test
    void buoyancyScalesWithMassSubmersionAndDeltaTime() {
        WaterPhysicsProfile light = liftOnlyProfile(100.0);
        WaterPhysicsProfile heavy = liftOnlyProfile(200.0);
        BuoyancySample quarterWet = wetSample(0.25, Vec3.ZERO);
        BuoyancySample halfWet = wetSample(0.50, Vec3.ZERO);

        double lightQuarter = HydrodynamicForces.velocityDelta(
                quarterWet, UNIT_BOUNDS, Vec3.ZERO, light, 0, 0.05
        ).y;
        double lightHalf = HydrodynamicForces.velocityDelta(
                halfWet, UNIT_BOUNDS, Vec3.ZERO, light, 0, 0.05
        ).y;
        double heavyHalf = HydrodynamicForces.velocityDelta(
                halfWet, UNIT_BOUNDS, Vec3.ZERO, heavy, 0, 0.05
        ).y;
        double longStep = HydrodynamicForces.velocityDelta(
                quarterWet, UNIT_BOUNDS, Vec3.ZERO, light, 0, 0.10
        ).y;

        assertTrue(lightHalf > lightQuarter);
        assertTrue(lightHalf > heavyHalf);
        assertEquals(lightQuarter * 2.0, longStep, 1.0e-12);
    }

    @Test
    void currentRelativeDragAcceleratesTowardFluidVelocity() {
        WaterPhysicsProfile dragOnly = dragOnlyProfile();
        BuoyancySample current = wetSample(1.0, new Vec3(2.0, 0.0, 0.0));

        Vec3 slowerThanWater = HydrodynamicForces.velocityDelta(
                current, UNIT_BOUNDS, Vec3.ZERO, dragOnly, 0, 0.05
        );
        Vec3 fasterThanWater = HydrodynamicForces.velocityDelta(
                current, UNIT_BOUNDS, new Vec3(0.2, 0.0, 0.0), dragOnly, 0, 0.05
        );

        assertTrue(slowerThanWater.x > 0.0);
        assertTrue(fasterThanWater.x < 0.0);
    }

    @Test
    void disabledRuntimeTuningSkipsEveryForce() {
        Vec3 delta = HydrodynamicForces.velocityDelta(
                wetSample(1.0, new Vec3(3.0, 0.0, 0.0)),
                UNIT_BOUNDS,
                Vec3.ZERO,
                HydrodynamicForces.BOAT_PROFILE,
                0,
                0.05,
                new HydrodynamicForces.RuntimeTuning(
                        false, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        );

        assertEquals(Vec3.ZERO, delta);
    }

    @Test
    void watercraftDragIsHullOriented() {
        WaterPhysicsProfile directional = profile(
                0.0, 0.10, 1.00,
                0.0, 0.0, 0.0,
                true
        );
        BuoyancySample sample = wetSample(0.5, Vec3.ZERO);

        Vec3 forward = HydrodynamicForces.watercraftVelocityDelta(
                sample, UNIT_BOUNDS, new Vec3(0.0, 0.0, 0.10), 0.0f,
                0.5, directional, 0, 0.05, HydrodynamicForces.RuntimeTuning.DEFAULT
        );
        Vec3 lateral = HydrodynamicForces.watercraftVelocityDelta(
                sample, UNIT_BOUNDS, new Vec3(0.10, 0.0, 0.0), 0.0f,
                0.5, directional, 0, 0.05, HydrodynamicForces.RuntimeTuning.DEFAULT
        );

        assertTrue(Math.abs(lateral.x) > Math.abs(forward.z));
    }

    @Test
    void planingLiftUsesTheAnimatedSurfaceNormal() {
        WaterPhysicsProfile planing = profile(
                0.0, 0.0, 0.0,
                0.08, 0.0, 0.0,
                true
        );
        BuoyancySample sloped = new BuoyancySample(
                true, false, 0.5, 0.5, Vec3.ZERO,
                new Vec3(0.30, 0.95, 0.0).normalize()
        );

        Vec3 delta = HydrodynamicForces.watercraftVelocityDelta(
                sloped, UNIT_BOUNDS, new Vec3(0.0, 0.0, 0.12), 0.0f,
                0.5, planing, 0, 0.05, HydrodynamicForces.RuntimeTuning.DEFAULT
        );

        assertTrue(delta.x > 0.0);
        assertTrue(delta.y > 0.0);
    }

    @Test
    void slammingOnlyAppliesDuringDownwardSurfaceEntry() {
        WaterPhysicsProfile slamming = profile(
                0.0, 0.0, 0.0,
                0.0, 0.80, 0.0,
                true
        );
        BuoyancySample halfWet = wetSample(0.5, Vec3.ZERO);

        Vec3 entering = HydrodynamicForces.watercraftVelocityDelta(
                halfWet, UNIT_BOUNDS, new Vec3(0.0, -0.20, 0.0), 0.0f,
                0.0, slamming, 0, 0.05, HydrodynamicForces.RuntimeTuning.DEFAULT
        );
        Vec3 alreadyWet = HydrodynamicForces.watercraftVelocityDelta(
                halfWet, UNIT_BOUNDS, new Vec3(0.0, -0.20, 0.0), 0.0f,
                0.5, slamming, 0, 0.05, HydrodynamicForces.RuntimeTuning.DEFAULT
        );

        assertTrue(entering.y > alreadyWet.y);
    }

    @Test
    void angularMomentumIsDampedAndBounded() {
        double yawVelocity = 0.0;
        for (int tick = 0; tick < 100; tick++) {
            yawVelocity = WatercraftDynamicsState.integrateYawVelocity(yawVelocity, 2.5, 0.05);
        }
        assertTrue(yawVelocity > 0.0);
        assertTrue(yawVelocity <= 1.6);

        double damped = WatercraftDynamicsState.integrateYawVelocity(yawVelocity, 0.0, 0.05);
        assertTrue(damped < yawVelocity);
    }

    private static BuoyancySample wetSample(double fraction, Vec3 current) {
        return new BuoyancySample(
                true,
                fraction >= 1.0,
                fraction,
                fraction,
                current,
                new Vec3(0.0, 1.0, 0.0)
        );
    }

    private static WaterPhysicsProfile liftOnlyProfile(double mass) {
        return new WaterPhysicsProfile(
                mass, 0.0, 0.0,
                1.0,
                0.0, 0.0, 0.0,
                1.0, 1.0, 1.0,
                0.0, 0.0, 0.0,
                1.0,
                false
        );
    }

    private static WaterPhysicsProfile dragOnlyProfile() {
        return new WaterPhysicsProfile(
                100.0, 0.0, 0.0,
                0.0,
                0.5, 0.5, 0.0,
                1.0, 1.0, 1.0,
                0.0, 0.0, 0.0,
                1.0,
                false
        );
    }

    private static WaterPhysicsProfile profile(
            double displacedVolumeScale,
            double longitudinalDrag,
            double lateralDrag,
            double planing,
            double slamming,
            double angularStability,
            boolean rigidWatercraft
    ) {
        return new WaterPhysicsProfile(
                100.0, 0.0, 0.0,
                displacedVolumeScale,
                longitudinalDrag, lateralDrag, 0.0,
                1.0, 1.0, 1.0,
                planing, slamming, angularStability,
                1.0,
                rigidWatercraft
        );
    }
}
