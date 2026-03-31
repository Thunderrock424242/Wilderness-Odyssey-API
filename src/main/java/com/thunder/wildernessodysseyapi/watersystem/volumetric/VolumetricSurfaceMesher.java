package com.thunder.wildernessodysseyapi.watersystem.volumetric;

import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SurfaceSample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts sparse per-column fluid samples into a triangle mesh description.
 * This is renderer-agnostic data that can be consumed by a future client shader pipeline.
 */
public final class VolumetricSurfaceMesher {

    private VolumetricSurfaceMesher() {
    }

    public static MeshSnapshot buildMesh(List<SurfaceSample> samples, double maxEdgeDelta) {
        if (samples == null || samples.isEmpty()) {
            return MeshSnapshot.empty();
        }

        Map<Long, SurfaceSample> byColumn = new HashMap<>();
        for (SurfaceSample sample : samples) {
            byColumn.put(columnKey(sample.blockX(), sample.blockZ()), sample);
        }

        int quads = 0;
        int triangles = 0;
        int skipped = 0;
        double area = 0.0D;

        for (SurfaceSample sample : samples) {
            SurfaceSample east = byColumn.get(columnKey(sample.blockX() + 1, sample.blockZ()));
            SurfaceSample south = byColumn.get(columnKey(sample.blockX(), sample.blockZ() + 1));
            SurfaceSample southEast = byColumn.get(columnKey(sample.blockX() + 1, sample.blockZ() + 1));

            if (east == null || south == null || southEast == null) {
                skipped++;
                continue;
            }

            double max = Math.max(Math.max(sample.surfaceY(), east.surfaceY()), Math.max(south.surfaceY(), southEast.surfaceY()));
            double min = Math.min(Math.min(sample.surfaceY(), east.surfaceY()), Math.min(south.surfaceY(), southEast.surfaceY()));
            if ((max - min) > maxEdgeDelta) {
                skipped++;
                continue;
            }

            quads++;
            triangles += 2;
            area += estimateQuadArea(sample, east, south, southEast);
        }

        return new MeshSnapshot(samples.size(), quads, triangles, skipped, area);
    }

    public static List<Triangle> buildTriangles(List<SurfaceSample> samples, double maxEdgeDelta) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }

        Map<Long, SurfaceSample> byColumn = new HashMap<>();
        for (SurfaceSample sample : samples) {
            byColumn.put(columnKey(sample.blockX(), sample.blockZ()), sample);
        }

        List<Triangle> triangles = new ArrayList<>();
        for (SurfaceSample sample : samples) {
            SurfaceSample east = byColumn.get(columnKey(sample.blockX() + 1, sample.blockZ()));
            SurfaceSample south = byColumn.get(columnKey(sample.blockX(), sample.blockZ() + 1));
            SurfaceSample southEast = byColumn.get(columnKey(sample.blockX() + 1, sample.blockZ() + 1));
            if (east == null || south == null || southEast == null) {
                continue;
            }

            double max = Math.max(Math.max(sample.surfaceY(), east.surfaceY()), Math.max(south.surfaceY(), southEast.surfaceY()));
            double min = Math.min(Math.min(sample.surfaceY(), east.surfaceY()), Math.min(south.surfaceY(), southEast.surfaceY()));
            if ((max - min) > maxEdgeDelta) {
                continue;
            }

            Vertex v0 = vertex(sample);
            Vertex v1 = vertex(east);
            Vertex v2 = vertex(south);
            Vertex v3 = vertex(southEast);

            triangles.add(new Triangle(v0, v1, v3));
            triangles.add(new Triangle(v0, v3, v2));
        }

        return List.copyOf(triangles);
    }

    private static Vertex vertex(SurfaceSample sample) {
        return new Vertex(
                sample.blockX() + 0.5D,
                sample.surfaceY() + sample.tideOffset(),
                sample.blockZ() + 0.5D,
                sample.volume(),
                sample.shorelineFactor(),
                sample.moonPhaseFactor()
        );
    }

    private static double estimateQuadArea(SurfaceSample a, SurfaceSample b, SurfaceSample c, SurfaceSample d) {
        Vertex v0 = vertex(a);
        Vertex v1 = vertex(b);
        Vertex v2 = vertex(c);
        Vertex v3 = vertex(d);
        return triangleArea(v0, v1, v3) + triangleArea(v0, v3, v2);
    }

    private static double triangleArea(Vertex a, Vertex b, Vertex c) {
        double abX = b.x() - a.x();
        double abY = b.y() - a.y();
        double abZ = b.z() - a.z();

        double acX = c.x() - a.x();
        double acY = c.y() - a.y();
        double acZ = c.z() - a.z();

        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;

        return 0.5D * Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    public record MeshSnapshot(int sampleCount, int quads, int triangles, int skippedColumns, double estimatedArea) {
        public static MeshSnapshot empty() {
            return new MeshSnapshot(0, 0, 0, 0, 0.0D);
        }
    }

    public record Vertex(double x, double y, double z, double volume, double shorelineFactor, double moonPhaseFactor) {
    }

    public record Triangle(Vertex a, Vertex b, Vertex c) {
    }
}
