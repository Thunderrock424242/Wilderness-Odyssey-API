package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** Chunk-local surface topology with a common outer-edge spacing. */
final class WaterMeshTopology {
    // Polygon order is NW, SW, SE, NE with optional edge midpoints. The mask
    // removes midpoints only on chunk outer edges, independently of neighbors.
    private static final float[][] POLYGONS = new float[16][];
    static {
        float[] xs = {0, 0, 1, 1};
        float[] zs = {0, 1, 1, 0};
        for (int mask = 0; mask < 16; mask++) {
            float[] polygon = new float[(8 - Integer.bitCount(mask)) * 2];
            int count = 0;
            for (int edge = 0; edge < 4; edge++) {
                polygon[count++] = xs[edge];
                polygon[count++] = zs[edge];
                if ((mask & (1 << edge)) == 0) {
                    polygon[count++] = (xs[edge] + xs[(edge + 1) % 4]) * 0.5f;
                    polygon[count++] = (zs[edge] + zs[(edge + 1) % 4]) * 0.5f;
                }
            }
            POLYGONS[mask] = polygon;
        }
    }

    private WaterMeshTopology() { }

    static void emitColumn(int localX, int localZ, int detail, QuadConsumer output) {
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16 || (detail != 1 && detail != 2)) {
            throw new IllegalArgumentException("Invalid water mesh column detail");
        }
        if (detail == 1) {
            output.emit(0, 0, 0, 1, 1, 1, 1, 0);
            return;
        }
        int mask = (localX == 0 ? 1 : 0) | (localZ == 15 ? 2 : 0)
                | (localX == 15 ? 4 : 0) | (localZ == 0 ? 8 : 0);
        float[] polygon = POLYGONS[mask];
        int count = polygon.length / 2;
        for (int edge = 0; edge < count; edge += 2) {
            int a = edge * 2;
            int b = ((edge + 1) % count) * 2;
            int c = edge + 2 < count ? (edge + 2) * 2 : 0;
            // Pair adjacent fan triangles into a BLOCK-format quad. An odd
            // boundary polygon ends with one deliberately degenerate triangle.
            if (edge == count - 1) c = b;
            output.emit(0.5f, 0.5f, polygon[a], polygon[a + 1], polygon[b], polygon[b + 1], polygon[c], polygon[c + 1]);
        }
    }

    @FunctionalInterface
    interface QuadConsumer {
        void emit(float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz);
    }
}
