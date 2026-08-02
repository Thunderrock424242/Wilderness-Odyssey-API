package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Guards chunk-local water vertices against far-world float precision loss. */
class WaterChunkMeshPrecisionTest {

    @Test
    void preservesHalfBlockSubdivisionFarFromWorldOrigin() {
        int chunkMinimum = 30_000_000;
        int worldCoordinate = chunkMinimum + 8;

        float local = WaterChunkMeshCache.localCoordinate(
                worldCoordinate, chunkMinimum, 0.5f);
        float absoluteFloat = worldCoordinate + 0.5f;

        assertEquals(8.5f, local, 0.0f);
        assertNotEquals(0.5f, absoluteFloat - worldCoordinate,
                "the regression fixture must demonstrate absolute-float precision loss");
    }
}
