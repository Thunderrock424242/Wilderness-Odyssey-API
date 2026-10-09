package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceOwnershipRegistryTest {
    @Test
    void retainsOverlappingSaveCandidatesInsteadOfChoosingAWinner() {
        var owners = PerformanceOwnershipRegistry.detect(Map.of(
                "shinoyuki_betterautosave", "0.20", "smoothchunk", "1.0", "fastasyncworldsave", "2.6"));
        var saves = owners.get(PerformanceArea.SAVE);
        assertEquals(3, saves.candidates().size());
        assertTrue(saves.overlap());
        assertFalse(saves.woTakeoverAllowed());
    }

    @Test
    void absenceOfAKnownModDoesNotGrantAnOptimizationOwner() {
        var owners = PerformanceOwnershipRegistry.detect(Map.of("unknown_optimizer", "1"));
        assertEquals(PerformanceArea.values().length, owners.size());
        assertTrue(owners.get(PerformanceArea.WORLDGEN).candidates().isEmpty());
        assertFalse(owners.get(PerformanceArea.WORLDGEN).woTakeoverAllowed());
    }

    @Test
    void recognizesC2meComponentsWithoutAssumingItsAsyncSaveOptionIsEnabled() {
        var owners = PerformanceOwnershipRegistry.detect(Map.of("c2me_opts_chunkio", "0.3"));
        assertEquals("c2me_opts_chunkio", owners.get(PerformanceArea.CHUNK_IO).candidates().getFirst().modId());
        assertEquals("activity unverified", owners.get(PerformanceArea.CHUNK_IO).candidates().getFirst().activity());
    }

    @Test
    void distinguishesComplementaryRenderingFromCompetingTerrainRenderers() {
        var complementary = PerformanceOwnershipRegistry.detect(Map.of("sodium", "1", "immediatelyfast", "1"));
        assertFalse(complementary.get(PerformanceArea.RENDERING).overlap());
        var competing = PerformanceOwnershipRegistry.detect(Map.of("sodium", "1", "embeddium", "1"));
        assertTrue(competing.get(PerformanceArea.RENDERING).overlap());
    }
}
