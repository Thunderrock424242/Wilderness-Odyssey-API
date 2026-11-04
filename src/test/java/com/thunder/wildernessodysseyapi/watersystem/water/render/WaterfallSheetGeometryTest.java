package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaterfallSheetGeometryTest {
    @Test
    void everyCurtainFaceWindsTowardItsExposedSide() {
        for (int[] normal : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            List<float[]> vertices = new ArrayList<>();
            WaterfallSheetGeometry.quad(normal[0], normal[1], 0, 0, 0, .4f,
                    (x, y, z, u, v) -> vertices.add(new float[]{x, y, z}));
            assertEquals(4, vertices.size());
            float[] a = vertices.get(0);
            float[] b = vertices.get(1);
            float[] c = vertices.get(2);
            double nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
            double nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            assertTrue(nx * normal[0] + nz * normal[1] > 0, "Curtain was back-face culled from its exposed side");
        }
    }

    @Test
    void verifiedCurtainProducesOnlyExposedWetFacesUnderOneBudget() {
        List<Integer> emitted = new ArrayList<>();
        int count = WaterfallSheetGeometry.emit(60, 64, 1.2f, 7, y -> true,
                (side, y) -> side != 0, (side, y, foam) -> {
                    assertNotEquals(0, side);
                    assertTrue(y >= 61 && y <= 64);
                    assertTrue(foam > 0 && foam <= 1);
                    emitted.add(y);
                });
        assertEquals(7, count);
        assertEquals(7, emitted.size());
    }

    @Test
    void gapOrDryOverrideRetiresTheWholeCurtainRatherThanBridgingIt() {
        int count = WaterfallSheetGeometry.emit(60, 64, 2, 128, y -> y != 62,
                (side, y) -> true, (side, y, foam) -> fail("Bridged a dry cell"));
        assertEquals(0, count);
    }

    @Test
    void metadataAloneAndOutOfEnvelopeGeometryEmitNothing() {
        for (float speed : new float[]{0, Float.NaN, -1}) {
            assertEquals(0, WaterfallSheetGeometry.emit(60, 64, speed, 128, y -> true,
                    (side, y) -> true, (side, y, foam) -> fail("No actual inflow")));
        }
        assertEquals(0, WaterfallSheetGeometry.emit(60, 78, 1, 128, y -> true,
                (side, y) -> true, (side, y, foam) -> fail("Exceeded supported fall height")));
    }

    @Test
    void noBudgetDoesNotInspectWorldInputs() {
        assertEquals(0, WaterfallSheetGeometry.emit(60, 64, 1, 0,
                y -> { fail("Zero budget read water"); return false; },
                (side, y) -> { fail("Zero budget read exposure"); return false; },
                (side, y, foam) -> fail("Zero budget emitted geometry")));
    }
}
