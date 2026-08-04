package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies deterministic local downhill selection without neighbor chunks. */
class DrainageDirectionCalculatorTest {

    @Test
    void selectsSteepestMeasuredDownhillEdge() {
        DrainageDirection direction = DrainageDirectionCalculator.calculate(
                100.0,
                98.0,
                97.0,
                91.0,
                95.0,
                99.0,
                96.0,
                94.0,
                93.0
        );

        assertEquals(DrainageDirection.EAST, direction);
    }

    @Test
    void flatOrUphillTerrainRemainsALocalSink() {
        DrainageDirection direction = DrainageDirectionCalculator.calculate(
                64.0,
                64.0,
                64.1,
                65.0,
                64.0,
                66.0,
                64.0,
                64.2,
                64.0
        );

        assertEquals(DrainageDirection.SINK, direction);
    }

    @Test
    void equalLowEdgesUseStableClockwiseOrder() {
        DrainageDirection direction = DrainageDirectionCalculator.calculate(
                80.0,
                72.0,
                75.0,
                72.0,
                75.0,
                75.0,
                75.0,
                75.0,
                75.0
        );

        assertEquals(DrainageDirection.NORTH, direction);
    }
}
