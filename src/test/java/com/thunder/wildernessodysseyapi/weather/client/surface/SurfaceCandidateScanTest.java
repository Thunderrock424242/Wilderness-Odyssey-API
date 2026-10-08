package com.thunder.wildernessodysseyapi.weather.client.surface;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceCandidateScanTest {
    @Test
    void dryInnerCandidatesCannotStarveOuterWetTerrain() {
        SurfaceCandidateScan scan = new SurfaceCandidateScan();
        scan.configure(0, 0, 24);
        var visited = new HashSet<SurfaceCandidateScan.Column>();
        boolean foundWet = false;
        for (int pass = 0; pass < 3; pass++) {
            for (int candidate = 0; candidate < 1024; candidate++) {
                var column = scan.next();
                visited.add(column);
                if (column.x() == 20 && column.z() == 0) foundWet = true;
            }
        }
        assertTrue(foundWet, "Repeated passes must progress past the dry inner radius");
        assertEquals(scan.size(), visited.size());
    }

    @Test
    void cameraMoveStartsNearTheNewCenterAndClearDropsCandidates() {
        SurfaceCandidateScan scan = new SurfaceCandidateScan();
        scan.configure(-10, 15, 3);
        assertEquals(new SurfaceCandidateScan.Column(-10, 15), scan.next());
        scan.next();
        scan.configure(-10, 15, 3);
        assertNotEquals(new SurfaceCandidateScan.Column(-10, 15), scan.next());
        scan.configure(-6, 20, 3);
        assertEquals(new SurfaceCandidateScan.Column(-6, 20), scan.next());
        scan.clear();
        assertEquals(0, scan.size());
    }
}
