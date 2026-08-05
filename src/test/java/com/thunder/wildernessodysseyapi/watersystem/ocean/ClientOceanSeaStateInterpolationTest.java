package com.thunder.wildernessodysseyapi.watersystem.ocean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the previous/current render lifecycle for synchronized sea cells. */
class ClientOceanSeaStateInterpolationTest {

    @Test
    void rendersBetweenPreviousAndCurrentTickEndpoints() {
        OceanSeaState.Sample calm = OceanSeaState.CALM;
        OceanSeaState.Sample storm = new OceanSeaState.Sample(
                1.0f, 0.0f, 1.0f, 18.0f, 1.8f, 2.4f, 0.85f, 1.0f
        );
        ClientOceanSeaState.CellState cell = new ClientOceanSeaState.CellState(
                calm, calm, storm);

        cell.advance();

        OceanSeaState.Sample current = calm.interpolate(storm, 0.12f);
        assertEquals(calm.strength(), cell.sample(0.0f).strength(), 1.0e-6f);
        assertEquals(
                calm.interpolate(current, 0.5f).strength(),
                cell.sample(0.5f).strength(),
                1.0e-6f
        );
        assertEquals(current.strength(), cell.sample(1.0f).strength(), 1.0e-6f);
    }
}
