package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual packed inputs and the mesh's NW-SE triangles without a world or GL context. */
class WaterSurfaceMeshSamplesTest {
    static final WaterSurfaceMeshSamples.Layout BLOCK = new WaterSurfaceMeshSamples.Layout(32, 0, 12, 28);

    @Test
    void followsBothTrianglesInsteadOfOneColumnOrBilinearHeight() {
        var mesh = capture(1, quad(0, 0, 1, new float[] {60, 64, 68, 72}));
        assertEquals(64, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::height));
        assertEquals(68, mesh.sample(0.75, 0.25, WaterSurfaceMeshSamples.Vertex::height));
        assertEquals(4104, mesh.sample(0.25, 0.75, vertex -> vertex.height() * vertex.height()));
    }

    @Test
    void selectsHalfBlockQuadAndDoesNotBorrowAcrossDryHoles() {
        var mesh = capture(2, quad(0.5f, 0.5f, 0.5f, new float[] {10, 20, 30, 40}));
        assertEquals(20, mesh.sample(0.75, 0.75, WaterSurfaceMeshSamples.Vertex::height));
        assertTrue(Float.isNaN(mesh.sample(0.25, 0.25, WaterSurfaceMeshSamples.Vertex::height)));
        assertTrue(Float.isNaN(mesh.sample(1.1, 0.75, WaterSurfaceMeshSamples.Vertex::height)));
    }

    @Test
    void decodesPackedDepthCurrentAndNormalizedBodyBytes() {
        ByteBuffer bytes = quad(0, 0, 1, new float[] {63, 63, 63, 63});
        int color = WaterSurfaceVertexData.encodeColor(0xFFFFFFFF, 0.45f, -0.1f, 0, 1.5f);
        for (int index = 0; index < 4; index++) {
            putPayload(bytes, index * 32, color, 12, 25, 31);
        }
        var mesh = capture(1, bytes);
        assertEquals(1.5f, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::depth), 0.000001f);
        assertEquals(0.45f, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::currentX), 0.000001f);
        assertEquals(-0.1f, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::currentZ), 0.000001f);
        assertEquals(68.0f / 127, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::continuity), 0.000001f);
        assertEquals(12.0f / 68, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::oceanWeight), 0.000001f);
        assertEquals(25.0f / 68, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::riverWeight), 0.000001f);
        assertEquals(31.0f / 68, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::lakeWeight), 0.000001f);
    }

    @Test
    void ownsCopiedDataAndPreservesSourceBufferPosition() {
        ByteBuffer bytes = quad(0, 0, 1, new float[] {60, 64, 68, 72});
        var mesh = capture(1, bytes);
        assertEquals(0, bytes.position());
        bytes.putFloat(4, -500);
        assertEquals(64, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::height));
    }

    @Test
    void keepsChunkEdgeCoordinatesAndRejectsInvalidQueries() {
        var mesh = capture(1, quad(15, 15, 1, new float[] {60, 64, 68, 72}));
        assertEquals(68, mesh.sample(16, 16, WaterSurfaceMeshSamples.Vertex::height));
        for (double x : new double[] {-0.01, 16.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertTrue(Float.isNaN(mesh.sample(x, 15.5, WaterSurfaceMeshSamples.Vertex::height)));
        }
    }

    @Test
    void rejectsUnsupportedOrMalformedTopology() {
        ByteBuffer bytes = quad(0, 0, 1, new float[] {60, 64, 68, 72});
        assertThrows(IllegalArgumentException.class, () -> capture(3, bytes));
        assertThrows(IllegalArgumentException.class, () -> WaterSurfaceMeshSamples.capture(bytes, 5, 1, BLOCK));
        assertThrows(IllegalArgumentException.class, () -> WaterSurfaceMeshSamples.capture(bytes, 8, 1, BLOCK));
        bytes.putFloat(0, 17);
        assertThrows(IllegalArgumentException.class, () -> capture(1, bytes));
    }

    @Test
    void consumesRealMinecraftBlockBufferWithoutAnotherPackingImplementation() {
        int color = WaterSurfaceVertexData.encodeColor(0xFFFFFFFF, 0.45f, -0.1f, 0, 1.5f);
        try (ByteBufferBuilder storage = new ByteBufferBuilder(128)) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            float[] xs = {0, 0, 1, 1};
            float[] zs = {0, 1, 1, 0};
            for (int index = 0; index < 4; index++) {
                builder.addVertex(xs[index], 63, zs[index]).setColor(color).setUv(0, 0).setLight(0)
                        .setNormal(0.1f, 0.2f, 0.25f);
            }
            var format = DefaultVertexFormat.BLOCK;
            var layout = new WaterSurfaceMeshSamples.Layout(format.getVertexSize(),
                    format.getOffset(VertexFormatElement.POSITION), format.getOffset(VertexFormatElement.COLOR),
                    format.getOffset(VertexFormatElement.NORMAL));
            try (MeshData uploaded = builder.buildOrThrow()) {
                var mesh = WaterSurfaceMeshSamples.capture(uploaded.vertexBuffer(), 4, 1, layout);
                assertEquals(63, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::height));
                assertEquals(1.5f, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::depth), 0.000001f);
                assertEquals(0.45f, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::currentX), 0.000001f);
                assertEquals(68.0f / 127, mesh.sample(0.25, 0.75, WaterSurfaceMeshSamples.Vertex::continuity), 0.000001f);
            }
        }
    }

    @Test
    void transitionTrianglesCoverTheirColumnAndPreserveInterpolatedHeight() {
        for (int detail : new int[] {1, 2}) for (int x : new int[] {0, 5, 15}) for (int z : new int[] {0, 7, 15}) {
            var mesh = capture(detail, transitionBytes(x, z, detail));
            for (float u : new float[] {0.1f, 0.5f, 0.9f}) for (float v : new float[] {0.1f, 0.5f, 0.9f}) {
                double px = x + (double) u;
                double pz = z + (double) v;
                assertEquals(63 + 0.2 * px + 0.1 * pz,
                        mesh.sample(px, pz, WaterSurfaceMeshSamples.Vertex::height), 0.00001);
            }
        }
    }

    static ByteBuffer transitionBytes(int x, int z, int detail) {
        var quads = WaterMeshTopologyTest.quads(x, z, detail);
        ByteBuffer bytes = ByteBuffer.allocate(quads.size() * 128).order(ByteOrder.nativeOrder());
        int index = 0;
        for (float[] quad : quads) for (int corner = 0; corner < 4; corner++) {
            float px = x + quad[corner * 2];
            float pz = z + quad[corner * 2 + 1];
            int offset = index++ * 32;
            bytes.putFloat(offset, px).putFloat(offset + 4, 63 + 0.2f * px + 0.1f * pz).putFloat(offset + 8, pz);
            putPayload(bytes, offset, 0xFFFFFFFF, 127, 0, 0);
        }
        return bytes;
    }

    static WaterSurfaceMeshSamples capture(int subdivisions, ByteBuffer bytes) {
        return WaterSurfaceMeshSamples.capture(bytes, bytes.remaining() / 32, subdivisions, BLOCK);
    }

    static ByteBuffer quad(float x, float z, float step, float[] heights) {
        ByteBuffer bytes = ByteBuffer.allocate(128).order(ByteOrder.nativeOrder());
        float[] xs = {x, x, x + step, x + step};
        float[] zs = {z, z + step, z + step, z};
        for (int index = 0; index < 4; index++) {
            int offset = index * 32;
            bytes.putFloat(offset, xs[index]).putFloat(offset + 4, heights[index]).putFloat(offset + 8, zs[index]);
            putPayload(bytes, offset, 0xFFFFFFFF, 127, 0, 0);
        }
        return bytes;
    }

    static void putPayload(ByteBuffer bytes, int offset, int color, int ocean, int river, int lake) {
        bytes.put(offset + 12, (byte) (color >>> 16));
        bytes.put(offset + 13, (byte) (color >>> 8));
        bytes.put(offset + 14, (byte) color);
        bytes.put(offset + 15, (byte) (color >>> 24));
        bytes.put(offset + 28, (byte) ocean).put(offset + 29, (byte) river).put(offset + 30, (byte) lake);
    }
}
