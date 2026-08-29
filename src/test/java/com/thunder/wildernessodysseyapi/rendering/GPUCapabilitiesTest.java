package com.thunder.wildernessodysseyapi.rendering;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GPUCapabilitiesTest {

    @Test
    void semanticPathsDependOnFeaturesRatherThanIdentityStrings() {
        GPUCapabilities capabilities = new GPUCapabilities(
                GPUCapabilities.GraphicsApi.OPENGL,
                "any vendor",
                "any renderer",
                "4.6",
                -1L,
                GPUCapabilities.MemoryEvidence.UNAVAILABLE,
                16_384,
                8,
                EnumSet.of(
                        GPUCapabilities.Feature.SHADER_PROGRAMS,
                        GPUCapabilities.Feature.FRAMEBUFFER_BLIT,
                        GPUCapabilities.Feature.DEPTH_TEXTURES,
                        GPUCapabilities.Feature.MULTIPLE_RENDER_TARGETS
                )
        );

        assertTrue(capabilities.supportsAdvancedReflections());
        assertTrue(capabilities.supportsHighQualityVolumetrics());
        assertFalse(capabilities.supportsComputeShaders());
        assertFalse(capabilities.supportsGpuTiming());
    }

    @Test
    void unavailableSnapshotNeverAdvertisesExpensivePaths() {
        assertFalse(GPUCapabilities.UNAVAILABLE.available());
        assertFalse(GPUCapabilities.UNAVAILABLE.supportsAdvancedReflections());
        assertFalse(GPUCapabilities.UNAVAILABLE.supportsHighQualityVolumetrics());
    }
}
