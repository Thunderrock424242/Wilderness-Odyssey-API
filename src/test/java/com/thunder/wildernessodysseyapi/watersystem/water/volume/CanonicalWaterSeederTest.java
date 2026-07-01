package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies seed statistics used by chunk seeding and /wowater diagnostics. */
class CanonicalWaterSeederTest {

    @Test
    void seedStatsCombineIndependentChunkResults() {
        CanonicalWaterSeeder.SeedStats first =
                new CanonicalWaterSeeder.SeedStats(256, 64, 8, 2, 1);
        CanonicalWaterSeeder.SeedStats second =
                new CanonicalWaterSeeder.SeedStats(128, 32, 4, 1, 2);

        CanonicalWaterSeeder.SeedStats combined = first.plus(second);

        assertEquals(384, combined.scannedColumns());
        assertEquals(96, combined.importedCells());
        assertEquals(12, combined.skippedTracked());
        assertEquals(3, combined.skippedWaterlogged());
        assertEquals(3, combined.loadedChunks());
    }

    @Test
    void countedChunkAddsOnlyChunkCounter() {
        CanonicalWaterSeeder.SeedStats stats =
                new CanonicalWaterSeeder.SeedStats(256, 64, 8, 2, 0).countedChunk();

        assertEquals(256, stats.scannedColumns());
        assertEquals(64, stats.importedCells());
        assertEquals(8, stats.skippedTracked());
        assertEquals(2, stats.skippedWaterlogged());
        assertEquals(1, stats.loadedChunks());
    }
}
