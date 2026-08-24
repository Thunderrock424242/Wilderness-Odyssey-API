package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveVegetationSyncServiceTest {

    @Test
    void skipsDiagnosticOnlyChanges() {
        VegetationClimateState before = state(0.50, 0.10, 0.10, 0.0, VegetationSeasonState.GROWING, 20L, 1, 5.0);
        VegetationClimateState after = state(0.50, 0.10, 0.10, 0.0, VegetationSeasonState.GROWING, 40L, 8, 500.0);

        assertFalse(ReactiveVegetationSyncService.shouldSynchronize(before, after));
    }

    @Test
    void publishesWhenAClientVisualBucketChanges() {
        VegetationClimateState before = state(0.50, 0.10, 0.10, 0.0, VegetationSeasonState.GROWING, 20L, 1, 5.0);
        VegetationClimateState after = state(0.05, 0.10, 0.90, 0.0, VegetationSeasonState.DRY, 40L, 1, 5.0);

        assertTrue(ReactiveVegetationSyncService.shouldSynchronize(before, after));
    }

    @Test
    void batchingRequiresRunningEnabledEngineAndNetworkToggle() {
        assertTrue(ReactiveVegetationSnapshotTransport.batchingAvailable(true, true, true));
        assertFalse(ReactiveVegetationSnapshotTransport.batchingAvailable(false, true, true));
        assertFalse(ReactiveVegetationSnapshotTransport.batchingAvailable(true, false, true));
        assertFalse(ReactiveVegetationSnapshotTransport.batchingAvailable(true, true, false));
    }

    private static VegetationClimateState state(
            double moisture,
            double rainfall,
            double drought,
            double storm,
            VegetationSeasonState season,
            long tick,
            int plants,
            double micros
    ) {
        return new VegetationClimateState(
                moisture,
                rainfall,
                drought,
                storm,
                season,
                tick,
                tick,
                plants,
                micros
        );
    }
}
