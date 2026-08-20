package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveVegetationSyncPayloadTest {

    @Test
    void carriesDimensionAndOnlyClientClimateFields() {
        ResourceLocation overworld = ResourceLocation.withDefaultNamespace("overworld");
        VegetationClimateState source = new VegetationClimateState(
                0.4,
                0.7,
                0.2,
                0.3,
                VegetationSeasonState.WET,
                200L,
                210L,
                17,
                900.0
        );

        ReactiveVegetationSyncPayload payload = ReactiveVegetationSyncPayload.from(
                overworld, -4, 12, 25L, source);
        VegetationClimateState decoded = payload.climateState();

        assertTrue(payload.matchesDimension(overworld));
        assertFalse(payload.matchesDimension(ResourceLocation.withDefaultNamespace("the_nether")));
        assertEquals(25L, payload.revision());
        assertEquals(source.seasonState(), decoded.seasonState());
        assertEquals(source.lastClimateUpdateTick(), decoded.lastClimateUpdateTick());
        assertEquals(0L, decoded.lastVegetationUpdateTick());
        assertEquals(0, decoded.plantsProcessed());
        assertEquals(0.0, decoded.averageProcessingMicros());
    }
}
