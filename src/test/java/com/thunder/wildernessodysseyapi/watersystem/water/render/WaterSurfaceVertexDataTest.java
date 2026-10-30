package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact surface metadata without requiring a Minecraft render context. */
class WaterSurfaceVertexDataTest {

    @Test
    void roundTripsBoundedCurrentShoreAndDepthMetadata() {
        int original = 0xB43A769C;

        int encoded = WaterSurfaceVertexData.encodeColor(original, 1.2f, -0.8f, 0.72f, 9.5f);

        assertEquals(1.5f, WaterSurfaceVertexData.decodeCurrentX(encoded), 1.0e-6f);
        assertEquals(-0.45f, WaterSurfaceVertexData.decodeCurrentZ(encoded), 1.0e-6f);
        assertEquals(5.0f / 7.0f, WaterSurfaceVertexData.decodeShoreFactor(encoded), 1.0e-6f);
        assertEquals(10.0f / 24.0f, WaterSurfaceVertexData.decodeDepthFactor(encoded), 1.0e-6f);
    }

    @Test
    void preservesShallowWaterAndShoalingDepths() {
        for (float depth : new float[] {0, 0.5f, 1.5f, 3, 6, 10, 16, 24}) {
            assertEquals(depth, decodedDepth(depth), 1.0e-6f, "Depth " + depth);
            assertEquals(depth, WaterSurfaceVertexData.quantizedDepth(depth), 1.0e-6f);
        }
    }

    @Test
    void selectsNearestDepthWithDeeperMidpointTies() {
        float[] depths = {0, 0.5f, 1.5f, 3, 6, 10, 16, 24};
        for (int index = 1; index < depths.length; index++) {
            float midpoint = (depths[index - 1] + depths[index]) * 0.5f;
            assertEquals(depths[index - 1], decodedDepth(midpoint - 0.001f), 1.0e-6f);
            assertEquals(depths[index], decodedDepth(midpoint), 1.0e-6f);
            assertEquals(depths[index], decodedDepth(midpoint + 0.001f), 1.0e-6f);
            assertEquals(depths[index - 1], WaterSurfaceVertexData.quantizedDepth(midpoint - 0.001f));
            assertEquals(depths[index], WaterSurfaceVertexData.quantizedDepth(midpoint));
        }
    }

    @Test
    void depthRemainsMonotonicAndBounded() {
        float previous = 0;
        for (int step = -100; step <= 3_000; step++) {
            float decoded = decodedDepth(step * 0.01f);
            assertTrue(decoded >= previous && decoded <= 24, "Step " + step);
            assertEquals(decoded, WaterSurfaceVertexData.quantizedDepth(step * 0.01f), 1.0e-6f);
            previous = decoded;
        }
        for (float depth : new float[] {Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, -1, -Float.MAX_VALUE}) {
            assertEquals(0, decodedDepth(depth), "Malformed depth " + depth);
            assertEquals(0, WaterSurfaceVertexData.quantizedDepth(depth));
        }
        assertEquals(24, decodedDepth(Float.MAX_VALUE));
    }

    @Test
    void changingDepthPreservesOtherPayloadsAndDisplayBits() {
        int original = 0xB43A769C;
        int baseline = WaterSurfaceVertexData.encodeColor(original, 0.45f, -0.1f, 0.72f, 0);
        for (float depth : new float[] {0, 0.5f, 1.5f, 3, 6, 10, 16, 24}) {
            int encoded = WaterSurfaceVertexData.encodeColor(original, 0.45f, -0.1f, 0.72f, depth);
            assertEquals(baseline & 0x00FFFFFF, encoded & 0x00FFFFFF);
            assertEquals(original & 0xF8F8F8F8, encoded & 0xF8F8F8F8);
        }
    }

    private static float decodedDepth(float depth) {
        int encoded = WaterSurfaceVertexData.encodeColor(0xFFFFFFFF, 0, 0, 0, depth);
        return WaterSurfaceVertexData.decodeDepthFactor(encoded) * 24;
    }

    @Test
    void stillWaterHasNoQuantizationDriftAndDisplayColorStaysClose() {
        int original = 0xD557A3EC;

        int encoded = WaterSurfaceVertexData.encodeColor(original, 0.0f, 0.0f, 0.0f, 0.0f);

        assertEquals(0.0f, WaterSurfaceVertexData.decodeCurrentX(encoded));
        assertEquals(0.0f, WaterSurfaceVertexData.decodeCurrentZ(encoded));
        assertTrue(Math.abs(WaterSurfaceVertexData.displayChannel((encoded >>> 16) & 0xFF)
                - ((original >>> 16) & 0xFF)) <= 4);
        assertTrue(Math.abs(WaterSurfaceVertexData.displayChannel((encoded >>> 8) & 0xFF)
                - ((original >>> 8) & 0xFF)) <= 4);
    }

    @Test
    void clampsNonFiniteAndOutOfRangeInputs() {
        int encoded = WaterSurfaceVertexData.encodeColor(
                0xFFFFFFFF, Float.NaN, Float.POSITIVE_INFINITY, 4.0f, 96.0f);

        assertEquals(0.0f, WaterSurfaceVertexData.decodeCurrentX(encoded));
        assertEquals(0.0f, WaterSurfaceVertexData.decodeCurrentZ(encoded));
        assertEquals(1.0f, WaterSurfaceVertexData.decodeShoreFactor(encoded));
        assertEquals(1.0f, WaterSurfaceVertexData.decodeDepthFactor(encoded));
    }
}
