package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Verifies synchronized sediment changes tint without corrupting mesh alpha. */
class WatershedSedimentColorTest {

    @Test
    void sedimentDarkensTowardBrownAndPreservesAlpha() {
        int clear = 0x9A2A78C4;
        int muddy = WaterChunkMeshCache.applySedimentColor(clear, 0.9f, 0.25f);

        assertEquals(clear & 0xFF000000, muddy & 0xFF000000);
        assertNotEquals(clear & 0x00FFFFFF, muddy & 0x00FFFFFF);
    }
}
