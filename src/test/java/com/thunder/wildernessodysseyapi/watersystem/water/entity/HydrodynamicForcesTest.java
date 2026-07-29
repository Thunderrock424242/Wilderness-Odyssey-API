package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
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
        HydrodynamicForces.ForceProfile light = liftOnlyProfile(100.0);
        HydrodynamicForces.ForceProfile heavy = liftOnlyProfile(200.0);
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
        HydrodynamicForces.ForceProfile dragOnly = dragOnlyProfile();
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
                new HydrodynamicForces.RuntimeTuning(false, 1.0, 1.0, 1.0)
        );

        assertEquals(Vec3.ZERO, delta);
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

    private static HydrodynamicForces.ForceProfile liftOnlyProfile(double mass) {
        return new HydrodynamicForces.ForceProfile(
                mass, 0.0, 0.0,
                1.0,
                0.0, 0.0,
                1.0, 1.0,
                1.0
        );
    }

    private static HydrodynamicForces.ForceProfile dragOnlyProfile() {
        return new HydrodynamicForces.ForceProfile(
                100.0, 0.0, 0.0,
                0.0,
                0.5, 0.0,
                1.0, 1.0,
                1.0
        );
    }
}
