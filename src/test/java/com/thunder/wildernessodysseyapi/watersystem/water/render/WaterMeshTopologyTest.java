package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class WaterMeshTopologyTest {
    @Test
    void fineAndCoarseChunkBordersHaveIdenticalVertexSpacing() {
        for (int edge = 0; edge < 4; edge++) {
            assertEquals(edgePoints(1, edge), edgePoints(2, edge));
            assertEquals(17, edgePoints(2, edge).size());
        }
    }

    @Test
    void allTransitionColumnsCoverOneBlockWithCorrectWinding() {
        for (int x : new int[] {0, 5, 15}) for (int z : new int[] {0, 7, 15}) {
            List<float[]> quads = quads(x, z, 2);
            assertFalse(quads.isEmpty());
            assertTrue(quads.size() <= 4);
            double area = 0;
            for (float[] q : quads) {
                double a = area(q, 0, 1, 2);
                double b = area(q, 2, 3, 0);
                assertTrue(a >= 0 && b >= 0, "Top-face winding");
                area += a + b;
            }
            assertEquals(1, area, 0.000001, "Column " + x + "," + z);
        }
    }

    @Test
    void distantTopologyReducesSubmittedTrianglesWithoutRemovingCoverage() {
        int coarse = 0;
        int fine = 0;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            coarse += quads(x, z, 1).size() * 2;
            fine += quads(x, z, 2).size() * 2;
        }
        assertEquals(512, coarse);
        assertEquals(2040, fine);
        assertTrue(coarse < fine * 0.26);
    }

    static List<float[]> quads(int x, int z, int detail) {
        List<float[]> result = new ArrayList<>();
        WaterMeshTopology.emitColumn(x, z, detail,
                (ax, az, bx, bz, cx, cz, dx, dz) -> result.add(new float[] {ax, az, bx, bz, cx, cz, dx, dz}));
        return result;
    }

    private static TreeSet<Float> edgePoints(int detail, int edge) {
        TreeSet<Float> points = new TreeSet<>();
        for (int offset = 0; offset < 16; offset++) {
            int x = edge == 0 ? 0 : edge == 1 ? 15 : offset;
            int z = edge == 2 ? 0 : edge == 3 ? 15 : offset;
            for (float[] q : quads(x, z, detail)) for (int i = 0; i < 4; i++) {
                float px = x + q[i * 2];
                float pz = z + q[i * 2 + 1];
                if (edge == 0 && px == 0 || edge == 1 && px == 16) points.add(pz);
                if (edge == 2 && pz == 0 || edge == 3 && pz == 16) points.add(px);
            }
        }
        return points;
    }

    private static double area(float[] q, int a, int b, int c) {
        return ((q[b * 2 + 1] - q[a * 2 + 1]) * (q[c * 2] - q[a * 2])
                - (q[b * 2] - q[a * 2]) * (q[c * 2 + 1] - q[a * 2 + 1])) * 0.5;
    }
}
