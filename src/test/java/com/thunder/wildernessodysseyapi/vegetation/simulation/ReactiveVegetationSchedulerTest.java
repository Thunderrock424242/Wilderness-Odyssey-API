package com.thunder.wildernessodysseyapi.vegetation.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveVegetationSchedulerTest {

    @Test
    void unloadedChunkIsRemovedBeforeItsDueTurn() {
        ReactiveVegetationScheduler.LevelRuntime runtime =
                new ReactiveVegetationScheduler.LevelRuntime();
        runtime.add(42L, 100L, 200);

        runtime.remove(42L);

        assertEquals(0, runtime.loadedCount());
        assertNull(runtime.pollDue(Long.MAX_VALUE));
    }

    @Test
    void largeLoadedAreaRetainsAFlatPerTickProbeBudget() {
        int chunksPerTick = VegetationWorkBudget.maximumChunksPerTick(1_024, 200);
        int defaultPlantProbes = chunksPerTick * 4;

        assertEquals(7, chunksPerTick);
        assertEquals(28, defaultPlantProbes);
        assertTrue(defaultPlantProbes < 1_024);
    }

    @Test
    void emptyDimensionHasNoScheduledWorkBudget() {
        assertEquals(0, VegetationWorkBudget.maximumChunksPerTick(0, 200));
    }
}
