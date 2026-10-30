package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSurfaceFluxTest {

    @Test
    void stillWaterDoesNotGenerateRequests() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{2048, 2048},
                new int[]{0}, new int[]{1}, new double[]{0}, 0.02);
        assertArrayEquals(new int[]{0}, plan.signedUnits());
        assertArrayEquals(new double[]{0}, plan.discharge());
    }

    @Test
    void pressureWetsAnEmptyCellWithoutAddingVolume() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{4096, 0},
                new int[]{0}, new int[]{1}, new double[]{0}, 0.02);
        assertTrue(plan.signedUnits()[0] > 0);
        assertTrue(plan.signedUnits()[0] < 4096);
        assertTrue(plan.discharge()[0] > 0);
    }

    @Test
    void allOutgoingFacesShareTheSameDonorAllowance() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{100, 0, 0, 0, 0},
                new int[]{0, 0, 0, 0}, new int[]{1, 2, 3, 4},
                new double[]{8, 8, 8, 8}, 1);
        assertTrue(Arrays.stream(plan.signedUnits()).sum() <= 100);
        assertTrue(Arrays.stream(plan.signedUnits()).allMatch(amount -> amount >= 0));
    }

    @Test
    void allIncomingFacesShareOnlyTheSnapshotCapacity() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{4096, 4096, 4000},
                new int[]{0, 1}, new int[]{2, 2}, new double[]{8, 8}, 1);
        assertTrue(Arrays.stream(plan.signedUnits()).sum() <= 96);
    }

    @Test
    void incomingWaterCannotBeSpentAgainInTheSameSubstep() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{4096, 0, 0},
                new int[]{0, 1}, new int[]{1, 2}, new double[]{8, 8}, 0.02);
        assertTrue(plan.signedUnits()[0] > 0);
        assertEquals(0, plan.signedUnits()[1]);
    }

    @Test
    void malformedMotionCannotCreateNonFiniteDischarge() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{4096, 0},
                new int[]{0}, new int[]{1}, new double[]{Double.NaN}, 0.02);
        assertTrue(Double.isFinite(plan.discharge()[0]));
        assertTrue(plan.signedUnits()[0] >= 0 && plan.signedUnits()[0] <= 4096);
        assertThrows(IllegalArgumentException.class, () -> CanonicalSurfaceFlux.plan(
                new int[]{-1, 0}, new int[]{0}, new int[]{1}, new double[]{0}, 0.02));
        assertThrows(IllegalArgumentException.class, () -> CanonicalSurfaceFlux.plan(
                new int[]{1, 0}, new int[]{0}, new int[]{1}, new double[]{0}, Double.NaN));
    }

    @Test
    void equalSurfacesAboveSteppedBedsRemainStill() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{8192, 4096}, new int[]{12288, 8192},
                new double[]{0, 1}, new double[]{3}, new int[]{0}, new int[]{1}, new double[]{0}, 0.02);
        assertArrayEquals(new int[]{0}, plan.signedUnits());
        assertArrayEquals(new double[]{0}, plan.discharge());
    }

    @Test
    void dryHighSillBlocksEvenStaleForwardMomentumUntilOvertopped() {
        var blocked = CanonicalSurfaceFlux.plan(new int[]{4096, 0}, new int[]{16384, 8192},
                new double[]{0, 2}, new double[]{4}, new int[]{0}, new int[]{1}, new double[]{8}, 0.02);
        assertEquals(0, blocked.signedUnits()[0]);
        var overtopped = CanonicalSurfaceFlux.plan(new int[]{12288, 0}, new int[]{16384, 8192},
                new double[]{0, 2}, new double[]{4}, new int[]{0}, new int[]{1}, new double[]{0}, 0.02);
        assertTrue(overtopped.signedUnits()[0] > 0);
    }

    @Test
    void multipleHighOutletsShareOnlyTheOvertoppingLayer() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{8602, 6144, 6144, 6144, 6144},
                new int[]{16384, 8192, 8192, 8192, 8192}, new double[]{0, 2, 2, 2, 2},
                new double[]{4, 4, 4, 4}, new int[]{0, 0, 0, 0}, new int[]{1, 2, 3, 4},
                new double[]{8, 8, 8, 8}, 1);
        int outgoing = Arrays.stream(plan.signedUnits()).sum();
        assertTrue(outgoing > 0 && outgoing <= 410, "Outlets spent water below the shared sill");
    }

    @Test
    void deepReceiversShareSnapshotColumnCapacity() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{16384, 16384, 8000},
                new int[]{16384, 16384, 8192}, new double[]{1, 1, 0}, new double[]{2, 2},
                new int[]{0, 1}, new int[]{2, 2}, new double[]{8, 8}, 0.02);
        assertTrue(Arrays.stream(plan.signedUnits()).sum() > 0);
        assertTrue(Arrays.stream(plan.signedUnits()).sum() <= 192);
    }

    @Test
    void aLowOpeningCapsDeepPressureDischarge() {
        var low = CanonicalSurfaceFlux.plan(new int[]{12288, 0}, new int[]{16384, 16384},
                new double[]{0, 0}, new double[]{1}, new int[]{0}, new int[]{1}, new double[]{0}, 0.02);
        var tall = CanonicalSurfaceFlux.plan(new int[]{12288, 0}, new int[]{16384, 16384},
                new double[]{0, 0}, new double[]{4}, new int[]{0}, new int[]{1}, new double[]{0}, 0.02);
        assertTrue(low.signedUnits()[0] > 0 && low.signedUnits()[0] < tall.signedUnits()[0]);
    }

    @Test
    void aNearlyDryHighOutletDoesNotFreezeTheLowerOutlet() {
        var plan = CanonicalSurfaceFlux.plan(new int[]{8193, 6144, 0},
                new int[]{16384, 8192, 16384}, new double[]{0, 2, 0}, new double[]{4, 4},
                new int[]{0, 0}, new int[]{1, 2}, new double[]{8, 8}, 0.02);
        assertTrue(plan.signedUnits()[0] <= 1);
        assertTrue(plan.signedUnits()[1] > 100, "The high sill incorrectly limited a lower opening");
    }
}
