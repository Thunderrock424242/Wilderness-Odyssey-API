package com.thunder.wildernessodysseyapi.worldgen.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bunker placement does not carry invalid block-entity data into chunks. */
class BunkerPlacementProcessorTest {

    @Test
    void stripsBlockEntityDataFromOrdinaryTerrain() {
        assertTrue(BunkerPlacementProcessor.shouldStripBlockEntityData(true, false));
    }

    @Test
    void preservesDataForRealBlockEntityBlocks() {
        assertFalse(BunkerPlacementProcessor.shouldStripBlockEntityData(true, true));
    }

    @Test
    void ignoresBlocksWithoutTemplateBlockEntityData() {
        assertFalse(BunkerPlacementProcessor.shouldStripBlockEntityData(false, false));
    }
}
