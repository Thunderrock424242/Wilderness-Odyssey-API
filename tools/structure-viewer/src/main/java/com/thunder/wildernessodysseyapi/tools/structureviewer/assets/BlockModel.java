package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import com.thunder.wildernessodysseyapi.tools.structureviewer.render.Vec3;
import java.util.List;

/** Cached local-space model geometry, independent of structure position and file format. */
public record BlockModel(List<Quad> quads, boolean occludes, boolean fallback) {
    public BlockModel { quads = List.copyOf(quads); }

    /** One textured face; cullSide is -1 unless its rotated neighbor direction is known. */
    public record Quad(List<Vec3> vertices, double[] uv, Texture texture, int tint, int cullSide, boolean shade) {
        public Quad { vertices = List.copyOf(vertices); uv = uv.clone(); }
    }
}
