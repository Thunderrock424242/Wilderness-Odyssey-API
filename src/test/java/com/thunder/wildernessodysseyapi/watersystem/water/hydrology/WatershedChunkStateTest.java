package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies compact state construction before persistence and synchronization. */
class WatershedChunkStateTest {

    @Test
    void initialAquiferStorageIsNotPackedAsSurfaceRunoff() {
        WatershedChunkState state = WatershedChunkState.create(
                1L,
                64,
                WatershedConditions.DrainageDirection.EAST,
                WatershedConditions.WaterFeature.RIVER,
                0.5f,
                0L,
                0.8f,
                20L,
                WatershedDrainageGrid.uniform(WatershedConditions.DrainageDirection.EAST),
                0.375f
        );

        assertEquals(0.375f, state.conditions().aquiferStorage(), 2.0e-5f);
        assertEquals(0.0f, state.conditions().storedRunoff(), 2.0e-5f);
    }
}
