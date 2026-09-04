package com.thunder.wildernessodysseyapi.worldgen.coast;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TropicalPalmPlacerTest {

    @Test
    void deterministicPalmsStaySmallAndEveryPartConnectsToTheTrunk() {
        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {0, 0}}) {
            var parts = TropicalPalmPlacer.shape(6, direction[0], direction[1]);
            assertEquals(parts, TropicalPalmPlacer.shape(6, direction[0], direction[1]));
            assertTrue(parts.size() < 64);
            assertTrue(parts.stream().allMatch(part -> Math.abs(part.x()) <= 4
                    && Math.abs(part.z()) <= 4 && part.y() >= 1 && part.y() <= 7));
            var connected = new HashSet<TropicalPalmPlacer.Part>();
            connected.add(parts.stream().filter(part -> part.trunk() && part.y() == 1)
                    .findFirst().orElseThrow());
            boolean changed;
            do {
                changed = false;
                for (var part : parts) {
                    if (!connected.contains(part) && connected.stream().anyMatch(other ->
                            Math.abs(part.x() - other.x()) + Math.abs(part.y() - other.y())
                                    + Math.abs(part.z() - other.z()) == 1)) {
                        connected.add(part);
                        changed = true;
                    }
                }
            } while (changed);
            assertEquals(parts.size(), connected.size(), "No floating trunk or detached fronds");
        }
    }

    @Test
    void heightRequestsAreClampedToTheAuthoredRange() {
        assertEquals(TropicalPalmPlacer.shape(5, 1, 0), TropicalPalmPlacer.shape(-10, 1, 0));
        assertEquals(TropicalPalmPlacer.shape(7, 1, 0), TropicalPalmPlacer.shape(100, 1, 0));
    }
}
