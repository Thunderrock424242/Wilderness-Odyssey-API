package com.thunder.wildernessodysseyapi.gpuprofiler.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuFormatEstimatorTest {

    @Test
    void estimatesRgba8TextureStorage() {
        assertEquals(16L * 1024L * 1024L,
                GpuFormatEstimator.textureBytes(32856, 2048, 2048, 6408, 5121));
    }

    @Test
    void estimatesFloatDepthTextureStorage() {
        assertEquals(1920L * 1080L * 4L,
                GpuFormatEstimator.textureBytes(6402, 1920, 1080, 6402, 5126));
    }

    @Test
    void estimatesDepthStencilRenderbufferStorage() {
        assertEquals(2560L * 1440L * 8L,
                GpuFormatEstimator.renderbufferBytes(36013, 2560, 1440));
    }

    @Test
    void honorsPackedPixelTypes() {
        assertEquals(64L * 64L * 4L,
                GpuFormatEstimator.textureBytes(-1, 64, 64, 34041, 34042));
    }

    @Test
    void ignoresEmptyAllocations() {
        assertEquals(0L, GpuFormatEstimator.textureBytes(32856, 0, 1024, 6408, 5121));
        assertEquals(0L, GpuFormatEstimator.renderbufferBytes(32856, 1024, 0));
    }
}
