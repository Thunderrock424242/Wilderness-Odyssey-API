package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import com.thunder.wildernessodysseyapi.tools.structureviewer.render.Vec3;
import java.util.List;

/** Cached local-space model geometry, independent of structure position and file format. */
public record BlockModel(List<Quad> quads, boolean occludes, boolean fallback) {
    public BlockModel { quads = List.copyOf(quads); }

    /** One textured face; cullSide is -1 unless its rotated neighbor direction is known. */
    public record Quad(List<Vec3> vertices, double[] uv, Texture texture, int tint, int cullSide, boolean shade,
                       Vec3 normal, Vec3 center, double lighting) {
        public Quad { vertices = List.copyOf(vertices); uv = uv.clone(); }
        /** Model-space normals and lighting are invariant across every placement and camera frame. */
        public Quad(List<Vec3> vertices, double[] uv, Texture texture, int tint, int cullSide, boolean shade) {
            this(vertices, uv, texture, tint, cullSide, shade, normal(vertices), center(vertices), lighting(vertices, shade));
        }
        private static Vec3 normal(List<Vec3> vertices) {
            Vec3 a = vertices.get(1).subtract(vertices.get(0)), b = vertices.get(2).subtract(vertices.get(0));
            return new Vec3(a.y()*b.z()-a.z()*b.y(), a.z()*b.x()-a.x()*b.z(), a.x()*b.y()-a.y()*b.x());
        }
        private static Vec3 center(List<Vec3> vertices) {
            Vec3 sum = new Vec3(0,0,0);
            for (Vec3 v : vertices) sum = sum.add(v);
            return sum.multiply(.25);
        }
        private static double lighting(List<Vec3> vertices, boolean shaded) {
            if (!shaded) return 1;
            Vec3 n = normal(vertices); double length = Math.sqrt(n.dot(n));
            return length < 1e-10 ? 1 : .78 + .17*Math.max(0,n.y()/length) + .05*Math.abs(n.z()/length);
        }
    }
}
