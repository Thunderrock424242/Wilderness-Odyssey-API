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
        assertEquals(3.0f / 7.0f, WaterSurfaceVertexData.decodeDepthFactor(encoded), 1.0e-6f);
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
