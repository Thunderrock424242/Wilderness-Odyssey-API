package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact tributary routing and confluence accumulation. */
class WatershedDrainageGridTest {

    @Test
    void downhillCellsAccumulateIntoAConfluence() {
        int[] heights = {
                9, 8, 9, 10,
                8, 4, 8, 9,
                9, 8, 9, 10,
                10, 9, 10, 11
        };

        WatershedDrainageGrid grid = WatershedDrainageGrid.fromHeights(
                heights, DrainageDirection.NORTH);

        assertEquals(DrainageDirection.SOUTH_EAST, grid.direction(0));
        assertEquals(DrainageDirection.SINK, grid.direction(5));
        assertTrue(grid.accumulation(5) >= 4);
        assertTrue(grid.confluence(5));
    }

    @Test
    void packedGridRoundTripPreservesEveryCell() {
        int[] heights = {16, 15, 14, 13, 15, 14, 13, 12, 14, 13, 12, 11, 13, 12, 11, 10};
        WatershedDrainageGrid original = WatershedDrainageGrid.fromHeights(
                heights, DrainageDirection.SOUTH_EAST);
        WatershedDrainageGrid decoded = new WatershedDrainageGrid(
                original.directionBits(), original.accumulationBits());

        for (int cell = 0; cell < WatershedDrainageGrid.CELL_COUNT; cell++) {
            assertEquals(original.direction(cell), decoded.direction(cell));
            assertEquals(original.accumulation(cell), decoded.accumulation(cell));
        }
    }
}
