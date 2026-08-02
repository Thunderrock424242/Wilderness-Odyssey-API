package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies CPU camera sampling follows the snapshot mesh's quad diagonal. */
class ClientWaterImmersionContinuityTest {

    @Test
    void interpolatesBothTrianglesAcrossNorthWestSouthEastDiagonal() {
        float northWest = 0.25f;
        float southWest = 0.50f;
        float southEast = 0.75f;
        float northEast = 1.00f;

        float southTriangle = ClientWaterImmersion.interpolateQuadContinuity(
                northWest, southWest, southEast, northEast, 0.25f, 0.75f);
        float northTriangle = ClientWaterImmersion.interpolateQuadContinuity(
                northWest, southWest, southEast, northEast, 0.75f, 0.25f);
        float diagonal = ClientWaterImmersion.interpolateQuadContinuity(
                northWest, southWest, southEast, northEast, 0.50f, 0.50f);

        assertEquals(0.50f, southTriangle, 1.0e-6f);
        assertEquals(0.75f, northTriangle, 1.0e-6f);
        assertEquals(0.50f, diagonal, 1.0e-6f);
    }
}
