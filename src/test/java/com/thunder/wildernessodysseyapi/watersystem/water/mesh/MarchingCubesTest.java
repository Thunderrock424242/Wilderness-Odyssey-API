package com.thunder.wildernessodysseyapi.watersystem.water.mesh;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies repeatable extraction while marching-cubes scratch storage is reused. */
class MarchingCubesTest {

    @Test
    void repeatedExtractionDoesNotLeakScratchValuesBetweenRuns() {
        DensityField field = oneCornerField();
        MarchingCubes marchingCubes = new MarchingCubes();

        float[] first = marchingCubes.extract(field);
        float[] second = marchingCubes.extract(field);

        assertTrue(first.length >= 18);
        assertEquals(0, first.length % 18);
        assertArrayEquals(first, second, 0.0f);

        Arrays.fill(field.values, 0.0f);
        assertEquals(0, marchingCubes.extract(field).length);
    }

    private static DensityField oneCornerField() {
        DensityField field = new DensityField();
        field.nx = 2;
        field.ny = 2;
        field.nz = 2;
        field.originX = 0.0f;
        field.originY = 0.0f;
        field.originZ = 0.0f;
        field.values = new float[8];
        field.values[0] = 2.0f;
        return field;
    }
}
