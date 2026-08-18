package com.thunder.wildernessodysseyapi.vegetation.state;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveVegetationStateTest {

    @Test
    void chunkAttachmentRoundTripPreservesClimateAndDiagnostics() {
        ReactiveVegetationState original = new ReactiveVegetationState();
        original.applyClimate(new VegetationClimateState(
                0.81,
                0.74,
                0.12,
                0.67,
                VegetationSeasonState.WET,
                12_400L,
                12_000L,
                3,
                14.0
        ));
        original.recordProcessing(12_400L, 4, 82_000L);

        CompoundTag encoded = original.serializeNBT(null);
        ReactiveVegetationState restored = new ReactiveVegetationState();
        restored.deserializeNBT(null, encoded);
        VegetationClimateState state = restored.snapshot();

        assertEquals(0.81, state.moisture(), 0.0001);
        assertEquals(0.74, state.recentRainfall(), 0.0001);
        assertEquals(0.12, state.droughtLevel(), 0.0001);
        assertEquals(0.67, state.stormIntensity(), 0.0001);
        assertEquals(VegetationSeasonState.WET, state.seasonState());
        assertEquals(12_400L, state.lastClimateUpdateTick());
        assertEquals(12_400L, state.lastVegetationUpdateTick());
        assertEquals(4, state.plantsProcessed());
        assertTrue(state.averageProcessingMicros() > 0.0);
    }

    @Test
    void malformedValuesRecoverToBoundedState() {
        CompoundTag malformed = new CompoundTag();
        malformed.putDouble("moisture", Double.NaN);
        malformed.putDouble("drought", 8.0);
        malformed.putString("season", "NOT_A_SEASON");
        malformed.putLong("last_climate_update", -10L);

        ReactiveVegetationState restored = new ReactiveVegetationState();
        restored.deserializeNBT(null, malformed);
        VegetationClimateState state = restored.snapshot();

        assertEquals(VegetationClimateState.DEFAULT.moisture(), state.moisture(), 0.0001);
        assertEquals(1.0, state.droughtLevel(), 0.0001);
        assertEquals(VegetationSeasonState.UNKNOWN, state.seasonState());
        assertEquals(0L, state.lastClimateUpdateTick());
    }
}
