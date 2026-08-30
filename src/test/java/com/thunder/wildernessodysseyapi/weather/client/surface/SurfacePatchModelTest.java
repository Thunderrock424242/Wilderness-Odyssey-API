package com.thunder.wildernessodysseyapi.weather.client.surface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfacePatchModelTest {

    @Test
    void connectedCornerFieldProducesSharedIrregularContour() {
        var triangles = SurfacePatchModel.triangulate(0.5F, -0.5F, -0.5F, 0.5F);

        assertFalse(triangles.isEmpty());
        assertTrue(triangles.size() < 8);
        assertTrue(triangles.stream().anyMatch(triangle ->
                (triangle.x1() > 0.0F && triangle.x1() < 1.0F)
                        || (triangle.x2() > 0.0F && triangle.x2() < 1.0F)));
    }

    @Test
    void fullyCoveredCellUsesFourCenterTrianglesInsteadOfOneSquareQuad() {
        var triangles = SurfacePatchModel.triangulate(1.0F, 1.0F, 1.0F, 1.0F);

        assertEquals(4, triangles.size());
    }

    @Test
    void puddleFlatnessRejectsAnyOneBlockStep() {
        assertTrue(SurfacePatchModel.flatEnough(70, 70, 70, 70, 70, 0));
        assertFalse(SurfacePatchModel.flatEnough(70, 70, 71, 70, 70, 0));
    }

    @Test
    void noiseIsContinuousAndDeterministicAtSharedWorldCorners() {
        float first = SurfacePatchModel.field(12.0D, -8.0D, 0.6D, 42L);
        float second = SurfacePatchModel.field(12.0D, -8.0D, 0.6D, 42L);

        assertEquals(first, second, 0.0F);
    }

    @Test
    void synchronizedCoverageGrowsAndShrinksTheSameWorldContour() {
        float dry = SurfacePatchModel.field(12.0D, -8.0D, 0.15D, 42L);
        float wet = SurfacePatchModel.field(12.0D, -8.0D, 0.85D, 42L);

        assertTrue(wet > dry);
    }
}
