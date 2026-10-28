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
}
