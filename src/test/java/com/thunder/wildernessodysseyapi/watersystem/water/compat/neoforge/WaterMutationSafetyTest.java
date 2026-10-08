package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WaterMutationSafetyTest {
    @Test
    void loadedTargetAloneDoesNotAdmitBorderCallback() {
        assertFalse(WaterMutationSafety.hasNeighborhood(15, 8, 2, (x, z) -> x == 0 && z == 0));
        assertTrue(WaterMutationSafety.hasNeighborhood(8, 8, 2, (x, z) -> x == 0 && z == 0));
    }

    @Test
    void negativeCornerChecksAllFourColumnsAndNoDistantOriginChunk() {
        Set<String> visited = new HashSet<>();
        assertTrue(WaterMutationSafety.hasNeighborhood(-160, -160, 2, (x, z) -> {
            visited.add(x + "," + z);
            return true;
        }));
        assertEquals(Set.of("-11,-11", "-11,-10", "-10,-11", "-10,-10"), visited);
    }

    @Test
    void firstUnavailableColumnStopsTheProbeWithoutRequestingMore() {
        int[] probes = {0};
        assertFalse(WaterMutationSafety.hasNeighborhood(15, 15, 2, (x, z) -> { probes[0]++; return false; }));
        assertEquals(1, probes[0]);
    }
}
