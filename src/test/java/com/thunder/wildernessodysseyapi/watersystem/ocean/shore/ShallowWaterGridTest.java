package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShallowWaterGridTest {

    @Test
    void dryCellsRejectFlowImpulses() {
        ShallowWaterGrid grid = new ShallowWaterGrid(5, 5, 1.0f);

        grid.addImpulse(2, 2, 3.0f, -2.0f);
        grid.step(0.05f, 1.0f);

        assertAll(
                () -> assertEquals(0.0f, grid.surface(2, 2), 1.0e-6f),
                () -> assertEquals(0.0f, grid.velocityX(2, 2), 1.0e-6f),
                () -> assertEquals(0.0f, grid.velocityZ(2, 2), 1.0e-6f)
        );
    }

    @Test
    void localizedImpulseProducesFiniteNeighbouringResponse() {
        ShallowWaterGrid grid = filledGrid(7, 2.0f);
        grid.addImpulse(3, 3, 1.5f, 0.0f);

        for (int i = 0; i < 20; i++) {
            grid.step(0.05f, 0.0f);
        }

        assertAll(
                () -> assertTrue(Float.isFinite(grid.surface(3, 3))),
                () -> assertTrue(Float.isFinite(grid.velocityX(3, 3))),
                () -> assertTrue(Math.abs(grid.surface(2, 3)) > 1.0e-6f
                        || Math.abs(grid.velocityX(2, 3)) > 1.0e-6f)
        );
    }

    @Test
    void openBoundaryApproachesOceanLevel() {
        ShallowWaterGrid grid = filledGrid(5, 3.0f);

        // A conservative inertial boundary rings before friction brings it to
        // equilibrium; it is not the old per-pass direct elevation overwrite.
        for (int i = 0; i < 600; i++) {
            grid.step(0.05f, 0.75f);
        }

        assertEquals(0.75f, grid.surface(0, 2), 0.02f);
        assertEquals(0.75f, grid.surface(2, 2), 0.02f);
        assertEquals(0, grid.balance().residual(), 1.0e-9);
    }

    @Test
    void closedBasinConservesVolumeUnderRepeatedStrongImpulses() {
        ShallowWaterGrid grid = filledGrid(7, 0.02f);
        closeBoundaries(grid);
        double initialVolume = grid.balance().representedVolume();

        for (int tick = 0; tick < 1_000; tick++) {
            grid.addImpulse(tick % 7, (tick * 3) % 7, 100.0f, -100.0f);
            grid.step(0.05f, 4.0f);
        }

        assertEquals(initialVolume, grid.balance().representedVolume(), 1.0e-9);
        assertEquals(0.0, grid.balance().boundaryInflow());
        assertEquals(0.0, grid.balance().boundaryOutflow());
        assertEquals(0.0, grid.balance().residual(), 1.0e-9);
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                assertTrue(grid.waterDepth(x, z) >= 0.0f);
            }
        }
    }

    @Test
    void stillLakeOverSteppedBedDoesNotGenerateCurrents() {
        ShallowWaterGrid grid = new ShallowWaterGrid(7, 7, 1.0f);
        closeBoundaries(grid);
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                float bed = -1.0f - (x % 3) * 0.5f - (z % 2) * 0.25f;
                grid.setTerrain(x, z, bed, -bed);
            }
        }

        for (int tick = 0; tick < 200; tick++) grid.step(0.05f, 0.0f);

        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                assertEquals(0.0f, grid.surface(x, z), 1.0e-6f);
                assertEquals(0.0f, grid.velocityX(x, z), 1.0e-6f);
                assertEquals(0.0f, grid.velocityZ(x, z), 1.0e-6f);
            }
        }
    }

    @Test
    void damReleaseWetsDryCellsWithoutInventingMinimumDepthWater() {
        ShallowWaterGrid grid = new ShallowWaterGrid(9, 5, 1.0f);
        closeBoundaries(grid);
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 9; x++) {
                grid.setTerrain(x, z, 0.0f, x < 3 ? 2.0f : 0.0f);
            }
        }
        double initialVolume = grid.balance().representedVolume();

        for (int tick = 0; tick < 100; tick++) grid.step(0.05f, 0.0f);

        assertTrue(grid.waterDepth(5, 2) > 0.01f);
        assertTrue(grid.waterDepth(1, 2) < 2.0f);
        assertEquals(initialVolume, grid.balance().representedVolume(), 1.0e-9);
        assertEquals(0.0, grid.balance().residual(), 1.0e-9);
    }

    @Test
    void dryBedHigherThanWaterSurfaceBlocksFlowUntilOvertopped() {
        ShallowWaterGrid grid = new ShallowWaterGrid(7, 5, 1.0f);
        closeBoundaries(grid);
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 7; x++) {
                grid.setTerrain(x, z, x == 3 ? 3.0f : 0.0f, x < 3 ? 2.0f : 0.0f);
            }
        }
        for (int tick = 0; tick < 100; tick++) grid.step(0.05f, 0.0f);

        assertEquals(2.0f, grid.waterDepth(2, 2));
        assertEquals(0.0f, grid.waterDepth(3, 2));
        assertEquals(0.0f, grid.waterDepth(4, 2));
        assertEquals(0.0, grid.balance().residual(), 1.0e-9);
    }

    @Test
    void oceanExchangeExplainsEveryVolumeChange() {
        ShallowWaterGrid grid = filledGrid(7, 2.0f);
        closeBoundaries(grid);
        grid.setBoundaryOpen(ShallowWaterGrid.Side.WEST, 3, true);

        for (int tick = 0; tick < 500; tick++) {
            grid.step(0.05f, (float) Math.sin(tick * 0.03) * 0.75f);
        }

        ShallowWaterGrid.Balance balance = grid.balance();
        assertTrue(balance.boundaryInflow() > 0.0);
        assertTrue(balance.boundaryOutflow() > 0.0);
        assertEquals(balance.bathymetryExchange() + balance.boundaryInflow() - balance.boundaryOutflow(),
                balance.representedVolume(), 1.0e-8);
        assertEquals(0.0, balance.residual(), 1.0e-8);
    }

    @Test
    void cflWorkCapRetainsAllDeferredTime() {
        ShallowWaterGrid grid = new ShallowWaterGrid(3, 3, 0.25f);
        closeBoundaries(grid);
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) grid.setRestDepth(x, z, 64.0f);
        }
        grid.step(1.0f, 0.0f);

        assertTrue(grid.balance().deferredSeconds() > 0.0);
        assertEquals(1.0, grid.balance().simulatedSeconds() + grid.balance().deferredSeconds(), 1.0e-12);
        for (int call = 0; call < 100 && grid.balance().deferredSeconds() > 0.0; call++) {
            grid.step(0.0f, 0.0f);
        }

        assertEquals(0.0, grid.balance().deferredSeconds());
        assertEquals(1.0, grid.balance().simulatedSeconds(), 1.0e-12);
        assertEquals(0.0, grid.balance().residual(), 1.0e-9);
    }

    @Test
    void bedRefreshPreservesWaterAndBlockedCellRemovalIsAccounted() {
        ShallowWaterGrid grid = filledGrid(5, 2.0f);
        double initialVolume = grid.balance().representedVolume();
        grid.setRestDepth(2, 2, 1.0f);

        assertEquals(initialVolume, grid.balance().representedVolume());
        assertEquals(2.0f, grid.waterDepth(2, 2));
        assertEquals(1.0f, grid.surface(2, 2));

        grid.setBlocked(2, 2);
        assertEquals(initialVolume - 2.0, grid.balance().representedVolume());
        assertEquals(initialVolume - 2.0, grid.balance().bathymetryExchange());
        assertEquals(0.0, grid.balance().residual(), 1.0e-9);
    }

    private static void closeBoundaries(ShallowWaterGrid grid) {
        for (ShallowWaterGrid.Side side : ShallowWaterGrid.Side.values()) grid.setBoundaryOpen(side, false);
    }

    private static ShallowWaterGrid filledGrid(int size, float depth) {
        ShallowWaterGrid grid = new ShallowWaterGrid(size, size, 1.0f);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                grid.setRestDepth(x, z, depth);
            }
        }
        return grid;
    }

    @Test
    void longStormAndMalformedInputsRemainFiniteWithBoundedMotion() {
        ShallowWaterGrid grid = filledGrid(7, 4.0f);
        grid.addImpulse(3, 3, Float.NaN, Float.POSITIVE_INFINITY);
        grid.step(Float.POSITIVE_INFINITY, 2);
        for (int tick = 0; tick < 12_000; tick++) {
            if (tick % 40 == 0) grid.addImpulse(3, 3, 100, -100);
            grid.step(tick % 100 == 0 ? 1000 : 0.05f, (float) Math.sin(tick * 0.03) * 2);
        }
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                assertTrue(Float.isFinite(grid.surface(x, z)));
                assertTrue(Math.abs(grid.velocityX(x, z)) <= 8.0f);
                assertTrue(Math.abs(grid.velocityZ(x, z)) <= 8.0f);
            }
        }
    }
}
