package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import java.util.ArrayList;
import java.util.List;

/** Clips animated shell faces outside a clear 3x3 aperture; physics does not depend on this geometry. */
public final class RiftApertureClip {
    private RiftApertureClip() { }

    public record Vertex(float x, float y, float z, float rx, float ry, float u, float v) {
        private Vertex interpolate(Vertex to, float t) {
            return new Vertex(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t,
                    rx + (to.rx - rx) * t, ry + (to.ry - ry) * t, u + (to.u - u) * t, v + (to.v - v) * t);
        }
    }

    /** Disjoint outside strips retain winding and UVs; the central square never emits vertices. */
    public static List<List<Vertex>> outside(List<Vertex> face) {
        List<List<Vertex>> result = new ArrayList<>(4);
        add(result, clip(face, true, -1.5F, false));
        List<Vertex> remaining = clip(face, true, -1.5F, true);
        add(result, clip(remaining, true, 1.5F, true));
        remaining = clip(remaining, true, 1.5F, false);
        add(result, clip(remaining, false, -1.5F, false));
        add(result, clip(remaining, false, 1.5F, true));
        return result;
    }

    private static void add(List<List<Vertex>> result, List<Vertex> polygon) {
        if (polygon.size() >= 3) result.add(polygon);
    }

    private static List<Vertex> clip(List<Vertex> polygon, boolean xAxis, float boundary, boolean greater) {
        if (polygon.isEmpty()) return List.of();
        List<Vertex> result = new ArrayList<>(6);
        Vertex previous = polygon.getLast();
        float before = (xAxis ? previous.rx : previous.ry) - boundary;
        for (Vertex current : polygon) {
            float after = (xAxis ? current.rx : current.ry) - boundary;
            boolean previousInside = greater ? before >= 0 : before <= 0;
            boolean currentInside = greater ? after >= 0 : after <= 0;
            if (previousInside != currentInside) result.add(previous.interpolate(current, before / (before - after)));
            if (currentInside) result.add(current);
            previous = current;
            before = after;
        }
        return result;
    }
}
