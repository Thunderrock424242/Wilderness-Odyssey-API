package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterfallHydraulicsTest {
    @Test
    void evenTheCenterOfAnEddyRespectsTheHorizontalSpeedBound() {
        var motion = WaterfallHydraulics.sample(16, 0, .002, 0, 0, 1, 0, 0, 600);
        assertTrue(Math.hypot(motion.x(), motion.z()) > 0);
        assertTrue(Math.hypot(motion.x(), motion.z()) <= 1.50001);
    }

    @Test
    void jetAcceleratesWithFallDistanceWithoutExceedingTheExistingSpeedCap() {
        var lip = sample(9.8, 0, 0, 1);
        var middle = sample(6, 0, 0, 1);
        var base = sample(2, 0, 0, 1);
        assertTrue(lip.y() < 0);
        assertTrue(middle.y() < lip.y());
        assertTrue(base.y() <= middle.y());
        assertTrue(base.y() >= -8);
    }

    @Test
    void plannedGeometryWithoutInflowCannotCreateAJetOrWhitewater() {
        assertEquals(WaterfallHydraulics.Motion.STILL, sample(2, 0, 0, 0));
        assertEquals(WaterfallHydraulics.Motion.STILL,
                WaterfallHydraulics.sample(10, 0, 0, 4, 0, Float.NaN, 0, 0, 0));
        assertEquals(WaterfallHydraulics.Motion.STILL, sample(11, 0, 0, 1));
        assertEquals(WaterfallHydraulics.Motion.STILL, sample(-3, 0, 0, 1));
    }

    @Test
    void receivingWaterGetsBoundedOutwardTurbulenceThatDecaysWithDistanceAndDepth() {
        var near = sample(0, 0.45, 0, 1);
        var deep = sample(-1.5, 0.45, 0, 1);
        var outside = sample(0, 4, 0, 1);
        assertTrue(near.x() > 0, "Impact moves receiving water away from the jet");
        assertTrue(Math.abs(near.y()) < 1, "A pool does not inherit the whole falling jet velocity");
        assertTrue(near.foam() > deep.foam());
        assertEquals(WaterfallHydraulics.Motion.STILL, outside);
        assertTrue(Math.hypot(near.x(), near.z()) <= 1.5);
    }

    @Test
    void theJetCannotPullEntitiesThroughDryAirBesideItsCurtain() {
        assertEquals(WaterfallHydraulics.Motion.STILL, sample(6, .75, 0, 1));
        var vertical = WaterfallHydraulics.sample(10, 0, 0, 4, 0, 0, 0, -2, 0);
        assertTrue(vertical.y() < -2, "An actual finite falling parcel supplies inflow too");
    }

    @Test
    void impactPatternUsesReducedTimeAndRemainsFiniteAtLongWorldTimes() {
        var early = WaterfallHydraulics.sample(10, 0, .4, 0, .3, 1, 0, 0, 30);
        var late = WaterfallHydraulics.sample(10, 0, .4, 0, .3, 1, 0, 0, 30 + 2400L * 100000000L);
        assertTrue(early.foam() > 0);
        assertEquals(early, late);
    }

    private static WaterfallHydraulics.Motion sample(double y, double x, double z, float inflow) {
        return WaterfallHydraulics.sample(10, 0, x, y, z, inflow, 0, 0, 10);
    }
}
