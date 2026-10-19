package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiverCurrentFieldTest {
    private static final RiverCurrentField.Sample EAST = new RiverCurrentField.Sample(7, 0.2f, 0, true);
    private static final RiverCurrentField.Sample SOUTH = new RiverCurrentField.Sample(7, 0, 0.2f, true);

    @Test
    void interpolatesThroughBendsWithoutAmplifyingDischargeVelocity() {
        var result = RiverCurrentField.interpolate(16, 16, EAST,
                (x, z) -> x == 0 ? EAST : SOUTH);
        assertEquals(0.1f, result.currentX(), 1.0e-6f);
        assertEquals(0.1f, result.currentZ(), 1.0e-6f);
        assertTrue(Math.hypot(result.currentX(), result.currentZ()) <= 0.2);
    }

    @Test
    void respectsSampleCentersAndNegativeCoordinates() {
        var result = RiverCurrentField.interpolate(-8, -8, SOUTH,
                (x, z) -> x == -1 && z == -1 ? SOUTH : EAST);
        assertEquals(0, result.currentX(), 1.0e-6f);
        assertEquals(0.2f, result.currentZ(), 1.0e-6f);
    }

    @Test
    void missingDryAndUnrelatedBasinsRetainTheHomeCurrent() {
        var otherBasin = new RiverCurrentField.Sample(8, -0.9f, -0.9f, true);
        assertEquals(EAST, RiverCurrentField.interpolate(16, 16, EAST, (x, z) -> otherBasin));
        assertEquals(EAST, RiverCurrentField.interpolate(16, 16, EAST, (x, z) -> null));
        assertEquals(EAST, RiverCurrentField.interpolate(16, 16, EAST,
                (x, z) -> new RiverCurrentField.Sample(7, -1, -1, false)));
    }

    @Test
    void opposingFlowsCanCancelWithoutCreatingMotion() {
        var result = RiverCurrentField.interpolate(16, 16, EAST,
                (x, z) -> x == 0 ? EAST : new RiverCurrentField.Sample(7, -0.2f, 0, true));
        assertEquals(0, result.currentX(), 1.0e-6f);
        assertEquals(0, result.currentZ(), 1.0e-6f);
    }

    @Test
    void detailedVelocityReplacesTheWholeBackgroundVector() {
        assertEquals(0.12f, RiverCurrentField.currentX(0.12f, 0, 0.12f), 1.0e-6f);
        assertEquals(0, RiverCurrentField.currentZ(0.12f, 0, 0.2f), 1.0e-6f);
        assertEquals(-0.07f, RiverCurrentField.currentZ(0, -0.07f, 0.2f), 1.0e-6f);
    }

    @Test
    void settledDetailUsesBackgroundWithoutWakingAnyFiniteCells() {
        assertEquals(2.4f, RiverCurrentField.currentX(0, 0, 0.12f), 1.0e-6f);
        assertEquals(4.0f, RiverCurrentField.currentZ(0, 0, 0.2f), 1.0e-6f);
        assertEquals(0, RiverCurrentField.currentX(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN));
    }

    @Test
    void packedRegionalVelocityBecomesPhysicalVelocityExactlyOnce() {
        assertEquals(1.2f, RiverCurrentField.currentX(0, 0, 0.06f), 1.0e-6f);
        assertEquals(1.2f, RiverCurrentField.currentX(1.2f, 0, 0.06f), 1.0e-6f);
        assertEquals(-0.6f, RiverCurrentField.currentZ(0, 0, -0.03f), 1.0e-6f);
    }

    @Test
    void dryHomeDoesNotQueryNeighbors() {
        var dry = new RiverCurrentField.Sample(7, 0, 0, false);
        assertEquals(dry, RiverCurrentField.interpolate(16, 16, dry,
                (x, z) -> { fail("dry current queried neighbor"); return EAST; }));
    }

    @Test
    void outOfRangeFiniteCoordinatesKeepHomeWithoutOverflowingChunkKeys() {
        assertEquals(EAST, RiverCurrentField.interpolate(Double.MAX_VALUE, 16, EAST,
                (x, z) -> { fail("out-of-range current queried neighbor"); return SOUTH; }));
        assertEquals(EAST, RiverCurrentField.interpolate(16, -Double.MAX_VALUE, EAST,
                (x, z) -> { fail("out-of-range current queried neighbor"); return SOUTH; }));
    }
}
