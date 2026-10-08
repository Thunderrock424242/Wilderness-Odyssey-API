package com.thunder.wildernessodysseyapi.weather.client.surface;

import java.util.ArrayList;
import java.util.List;

/** Resumes bounded center-out terrain work so dry inner columns cannot starve the edge. */
final class SurfaceCandidateScan {
    private List<Column> offsets = List.of();
    private int radius = -1;
    private int centerX;
    private int centerZ;
    private int cursor;

    void configure(int x, int z, int radius) {
        if (this.radius != radius) {
            List<Column> columns = new ArrayList<>();
            for (int ring = 0; ring <= radius; ring++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring
                                && (long) dx * dx + (long) dz * dz <= (long) radius * radius) {
                            columns.add(new Column(dx, dz));
                        }
                    }
                }
            }
            offsets = List.copyOf(columns);
            cursor = 0;
        }
        if (centerX != x || centerZ != z) cursor = 0;
        centerX = x;
        centerZ = z;
        this.radius = radius;
    }

    Column next() {
        Column offset = offsets.get(cursor);
        cursor = (cursor + 1) % offsets.size();
        return new Column(centerX + offset.x, centerZ + offset.z);
    }

    int size() { return offsets.size(); }
    void clear() { offsets = List.of(); radius = -1; cursor = 0; }
    record Column(int x, int z) { }
}
