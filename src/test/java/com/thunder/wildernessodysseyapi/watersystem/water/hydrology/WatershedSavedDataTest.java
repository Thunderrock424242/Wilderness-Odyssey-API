package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact state quantization and versioned save/reload behavior. */
class WatershedSavedDataTest {

    @Test
    void packedStateAndSavedDataRoundTripConditions() {
        long key = ChunkPos.asLong(-7, 12);
        WatershedChunkState state = WatershedChunkState.create(
                0x71A2B3C4D5E6F708L,
                -34,
                DrainageDirection.NORTH_WEST,
                WaterFeature.LAKE,
                0.68f,
                445566L,
                0.84f,
                120L,
                WatershedDrainageGrid.fromHeights(
                        new int[]{9, 8, 9, 10, 8, 4, 8, 9, 9, 8, 9, 10, 10, 9, 10, 11},
                        DrainageDirection.NORTH_WEST
                )
        );
        state.apply(new WatershedSimulationModel.Result(
                0.77f,
                0.63f,
                0.52f,
                0.41f,
                0.28f,
                0.91f,
                true,
                0.72f,
                0.36f,
                -0.48f,
                -0.48f,
                0.57f,
                0.12f,
                0.33f
        ), 0.84f, 160L);
        state.setActiveFloodCells(9);

        WatershedSavedData data = new WatershedSavedData();
        data.store(key, state);
        CompoundTag encoded = data.save(new CompoundTag(), null);
        WatershedSavedData decoded = WatershedSavedData.load(encoded, null);
        WatershedConditions conditions = decoded.conditions(-7, 12);

        assertEquals(state.conditions().basinId(), conditions.basinId());
        assertEquals(-34, conditions.averageTerrainElevation());
        assertEquals(DrainageDirection.NORTH_WEST, conditions.downstreamDirection());
        assertEquals(WaterFeature.LAKE, conditions.waterFeature());
        assertEquals(9, conditions.activeTemporaryFloodCells());
        assertTrue(conditions.flooding());
        assertEquals(state.conditions().riverDischarge(), conditions.riverDischarge(), 0.0001f);
        assertEquals(state.conditions().waterLevelOffset(), conditions.waterLevelOffset(), 0.0001f);
        assertEquals(state.conditions().recentSnowmelt(), conditions.recentSnowmelt(), 0.0001f);
        assertEquals(
                state.drainageGrid().directionBits(),
                decoded.state(key).drainageGrid().directionBits()
        );
    }
}
