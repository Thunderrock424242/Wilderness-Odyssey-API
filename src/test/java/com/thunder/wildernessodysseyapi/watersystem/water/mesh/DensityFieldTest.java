package com.thunder.wildernessodysseyapi.watersystem.water.mesh;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies smooth density-gradient normals used by the SPH surface mesh. */
class DensityFieldTest {

    @Test
    void outwardNormalOpposesIncreasingDensity() {
        DensityField field = new DensityField();
        field.nx = 5;
        field.ny = 5;
        field.nz = 5;
        field.values = new float[field.nx * field.ny * field.nz];
        for (int z = 0; z < field.nz; z++) {
            for (int y = 0; y < field.ny; y++) {
                for (int x = 0; x < field.nx; x++) {
                    field.values[x + field.nx * (y + field.ny * z)] = x;
                }
            }
        }

        Vector3f normal = new Vector3f();
        assertTrue(field.outwardNormal(0.44f, 0.44f, 0.44f, normal));
        assertEquals(-1.0f, normal.x, 1.0e-5f);
        assertEquals(0.0f, normal.y, 1.0e-5f);
        assertEquals(0.0f, normal.z, 1.0e-5f);
    }

    @Test
    void analyticTrilinearGradientUsesAllThreeAxes() {
        DensityField field = new DensityField();
        field.nx = 5;
        field.ny = 5;
        field.nz = 5;
        field.values = new float[field.nx * field.ny * field.nz];
        for (int z = 0; z < field.nz; z++) {
            for (int y = 0; y < field.ny; y++) {
                for (int x = 0; x < field.nx; x++) {
                    field.values[x + field.nx * (y + field.ny * z)] = 2.0f * x + 3.0f * y + 4.0f * z;
                }
            }
        }

        Vector3f normal = new Vector3f();
        assertTrue(field.outwardNormal(0.55f, 0.66f, 0.77f, normal));
        float inverseLength = 1.0f / (float) Math.sqrt(29.0f);
        assertEquals(-2.0f * inverseLength, normal.x, 1.0e-5f);
        assertEquals(-3.0f * inverseLength, normal.y, 1.0e-5f);
        assertEquals(-4.0f * inverseLength, normal.z, 1.0e-5f);
    }
}
