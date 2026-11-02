package com.thunder.wildernessodysseyapi.watersystem.water.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable CPU sampling data captured from one uploaded BLOCK-format mesh.
 * It owns no world, native buffer or GL resource. Lookup checks at most eight
 * triangles in one column and evaluates only the selected three vertices.
 */
final class WaterSurfaceMeshSamples {
    private final int triangleSlots;
    private final int[] triangles;
    private final byte[] triangleCounts;
    private final int[] payloads;

    private WaterSurfaceMeshSamples(int subdivisions, int[] payloads) {
        this.triangleSlots = 2 * subdivisions * subdivisions;
        this.triangles = new int[256 * triangleSlots];
        this.triangleCounts = new byte[256];
        this.payloads = payloads;
    }

    /** Copies the actual vertex bytes before the existing upload releases them. */
    static WaterSurfaceMeshSamples capture(ByteBuffer source, int vertexCount, int subdivisions, Layout layout) {
        if ((subdivisions != 1 && subdivisions != 2) || vertexCount < 0 || vertexCount % 4 != 0
                || vertexCount > 16 * 16 * subdivisions * subdivisions * 4
                || (long) vertexCount * layout.stride() > source.remaining()) {
            throw new IllegalArgumentException("Invalid water mesh size or subdivision");
        }
        ByteBuffer bytes = source.duplicate().order(ByteOrder.nativeOrder());
        int start = bytes.position();
        int[] data = new int[vertexCount * 3];
        WaterSurfaceMeshSamples result = new WaterSurfaceMeshSamples(subdivisions, data);
        for (int quad = 0; quad < vertexCount; quad += 4) {
            for (int corner = 0; corner < 4; corner++) {
                int offset = start + (quad + corner) * layout.stride();
                int position = offset + layout.position();
                float x = bytes.getFloat(position);
                float z = bytes.getFloat(position + 8);
                float height = bytes.getFloat(position + 4);
                if (!Float.isFinite(x) || !Float.isFinite(z) || x < 0 || x > 16 || z < 0 || z > 16
                        || x * subdivisions != Math.round(x * subdivisions)
                        || z * subdivisions != Math.round(z * subdivisions) || !Float.isFinite(height)) {
                    throw new IllegalArgumentException("Invalid water mesh corner");
                }
                int color = offset + layout.color();
                int normal = offset + layout.normal();
                int target = (quad + corner) * 3;
                data[target] = Float.floatToRawIntBits(height);
                data[target + 1] = (Byte.toUnsignedInt(bytes.get(color + 3)) << 24)
                        | (Byte.toUnsignedInt(bytes.get(color)) << 16)
                        | (Byte.toUnsignedInt(bytes.get(color + 1)) << 8)
                        | Byte.toUnsignedInt(bytes.get(color + 2));
                // Three nonnegative SNORM channels need 21 bits. Their spare
                // bits retain the 33x33 half-grid position (11 bits), keeping
                // this copy at three words per vertex even for transition fans.
                int grid = Math.round(x * 2) + Math.round(z * 2) * 33;
                data[target + 2] = Math.max(0, bytes.get(normal))
                        | (Math.max(0, bytes.get(normal + 1)) << 7)
                        | (Math.max(0, bytes.get(normal + 2)) << 14) | (grid << 21);
            }
            result.addTriangle(quad, quad + 1, quad + 2, quad << 1);
            result.addTriangle(quad + 2, quad + 3, quad, (quad << 1) | 1);
        }
        return result;
    }

    private void addTriangle(int a, int b, int c, int reference) {
        double area = (x(b) - x(a)) * (z(c) - z(a)) - (z(b) - z(a)) * (x(c) - x(a));
        if (area == 0) return; // An odd transition polygon has one unused QUADS triangle.
        float minimumX = Math.min(x(a), Math.min(x(b), x(c)));
        float minimumZ = Math.min(z(a), Math.min(z(b), z(c)));
        int cellX = Math.min(15, (int) minimumX);
        int cellZ = Math.min(15, (int) minimumZ);
        if (area > 0 || Math.max(x(a), Math.max(x(b), x(c))) > cellX + 1
                || Math.max(z(a), Math.max(z(b), z(c))) > cellZ + 1) {
            throw new IllegalArgumentException("Invalid water mesh triangle");
        }
        int column = cellX + cellZ * 16;
        int count = triangleCounts[column];
        if (count >= triangleSlots) throw new IllegalArgumentException("Too many water triangles in one column");
        triangles[column * triangleSlots + count] = reference;
        triangleCounts[column]++;
    }

    /**
     * Interpolates evaluated vertex values over the actual emitted triangle.
     * Coordinates are on the undisturbed horizontal parameter plane. This does
     * not invert Gerstner horizontal displacement at a world-space camera ray.
     * An absent quad or out-of-chunk query returns NaN for caller fallback.
     */
    float sample(double x, double z, VertexField field) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || x < 0 || z < 0 || x > 16 || z > 16) {
            return Float.NaN;
        }
        int column = Math.min(15, (int) x) + Math.min(15, (int) z) * 16;
        for (int index = 0; index < triangleCounts[column]; index++) {
            int reference = triangles[column * triangleSlots + index];
            int first = reference >>> 1;
            boolean second = (reference & 1) != 0;
            int a = first + (second ? 2 : 0);
            int b = first + (second ? 3 : 1);
            int c = first + (second ? 0 : 2);
            double denominator = (z(b) - z(c)) * (x(a) - x(c)) + (x(c) - x(b)) * (z(a) - z(c));
            double wa = ((z(b) - z(c)) * (x - x(c)) + (x(c) - x(b)) * (z - z(c))) / denominator;
            double wb = ((z(c) - z(a)) * (x - x(c)) + (x(a) - x(c)) * (z - z(c))) / denominator;
            double wc = 1 - wa - wb;
            if (wa >= -1.0e-7 && wb >= -1.0e-7 && wc >= -1.0e-7) {
                return (float) (field.sample(vertex(a)) * wa + field.sample(vertex(b)) * wb + field.sample(vertex(c)) * wc);
            }
        }
        return Float.NaN;
    }

    static float interpolateTriangle(float northWest, float southWest, float southEast, float northEast,
                                     float u, float v) {
        if (u <= v) return northWest * (1 - v) + southWest * (v - u) + southEast * u;
        return northWest * (1 - u) + southEast * v + northEast * (u - v);
    }

    private float x(int index) { return ((payloads[index * 3 + 2] >>> 21) % 33) * 0.5f; }
    private float z(int index) { return ((payloads[index * 3 + 2] >>> 21) / 33) * 0.5f; }

    private Vertex vertex(int index) {
        int offset = index * 3;
        int body = payloads[offset + 2];
        int normal = (body & 127) | (((body >>> 7) & 127) << 8) | (((body >>> 14) & 127) << 16);
        return new Vertex(x(index), z(index), Float.intBitsToFloat(payloads[offset]), payloads[offset + 1], normal);
    }

    @FunctionalInterface
    interface VertexField {
        float sample(Vertex vertex);
    }

    /** Layout offsets come from the actual VertexFormat, not a second format definition. */
    record Layout(int stride, int position, int color, int normal) {
        Layout {
            if (stride <= 0 || position < 0 || color < 0 || normal < 0
                    || position > stride - 12 || color > stride - 4 || normal > stride - 3) {
                throw new IllegalArgumentException("Invalid water vertex layout");
            }
        }
    }

    /** Decodes the same normalized signed bytes and color payloads as the vertex shader. */
    record Vertex(float x, float z, float height, int color, int bodyNormal) {
        private float body(int shift) {
            return Math.max(0, (byte) (bodyNormal >>> shift) / 127.0f);
        }

        float continuity() { return Math.min(1, body(0) + body(8) + body(16)); }
        float oceanWeight() { return body(0) / Math.max(continuity(), 0.0001f); }
        float riverWeight() { return body(8) / Math.max(continuity(), 0.0001f); }
        float lakeWeight() { return body(16) / Math.max(continuity(), 0.0001f); }
        float depth() { return WaterSurfaceVertexData.decodeDepthFactor(color) * WaterSurfaceVertexData.DEPTH_NORMALIZATION_BLOCKS; }
        float currentX() { return WaterSurfaceVertexData.decodeCurrentX(color); }
        float currentZ() { return WaterSurfaceVertexData.decodeCurrentZ(color); }
    }
}
