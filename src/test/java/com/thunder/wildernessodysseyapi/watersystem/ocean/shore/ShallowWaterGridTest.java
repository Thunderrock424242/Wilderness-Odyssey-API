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

        for (int i = 0; i < 30; i++) {
            grid.step(0.05f, 0.75f);
        }

        assertTrue(grid.surface(0, 2) > 0.6f);
        assertTrue(grid.surface(2, 2) > 0.0f);
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
}
