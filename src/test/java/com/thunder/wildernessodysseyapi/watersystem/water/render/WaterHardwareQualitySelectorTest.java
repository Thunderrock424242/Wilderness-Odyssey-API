package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies conservative automatic water-quality selection across common hardware classes. */
class WaterHardwareQualitySelectorTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    void highEndBalancedSystemSelectsCinematic() {
        var selection = select(modernGpu("Device A", 24, GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL),
                24, 32, 8, 2560, 1440);

        assertEquals(WaterRenderingConfig.WaterQuality.CINEMATIC, selection.quality());
        assertTrue(selection.summary().contains("GPU cinematic"));
    }

    @Test
    void limitedMinecraftHeapCapsOtherwiseHighEndSystem() {
        var selection = select(modernGpu("Device A", 24, GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL),
                24, 32, 4, 2560, 1440);

        assertEquals(WaterRenderingConfig.WaterQuality.HIGH, selection.quality());
    }

    @Test
    void safeEightGigabyteCapabilitySetSelectsHighInsteadOfCinematic() {
        var selection = select(modernGpu("Device B", 8, GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL),
                16, 32, 8, 1920, 1080);

        assertEquals(WaterRenderingConfig.WaterQuality.HIGH, selection.quality());
    }

    @Test
    void vendorAndProductNamesDoNotChangeSelection() {
        var first = select(modernGpu("Unfamiliar Device", 8,
                GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL), 16, 32, 8, 1920, 1080);
        var second = select(modernGpu("Completely Different Name", 8,
                GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL), 16, 32, 8, 1920, 1080);

        assertEquals(first.quality(), second.quality());
    }

    @Test
    void unavailableAndPartialCapabilitiesRemainConservative() {
        assertEquals(WaterRenderingConfig.WaterQuality.LOW,
                select(GPUCapabilities.UNAVAILABLE, 24, 64, 12, 1920, 1080).quality());
        assertEquals(WaterRenderingConfig.WaterQuality.MEDIUM,
                select(basicGpu(), 16, 32, 8, 1920, 1080).quality());
    }

    @Test
    void availableOnlyMemoryCannotAutoPromoteToCinematic() {
        assertEquals(WaterRenderingConfig.WaterQuality.HIGH,
                select(modernGpu("Device C", 24, GPUCapabilities.MemoryEvidence.AVAILABLE_ONLY),
                        24, 32, 8, 1920, 1080).quality());
    }

    @Test
    void ultrawideOrFourKDisplayCapsCinematicAtHigh() {
        assertEquals(WaterRenderingConfig.WaterQuality.HIGH,
                select(modernGpu("Device D", 24, GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL),
                        24, 32, 8, 3840, 2160).quality());
    }

    @Test
    void cpuAndRamCanCapAnExpensiveGpu() {
        assertEquals(WaterRenderingConfig.WaterQuality.MEDIUM,
                select(modernGpu("Device E", 24, GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL),
                        4, 16, 4, 1920, 1080).quality());
        assertEquals(WaterRenderingConfig.WaterQuality.LOW,
                select(modernGpu("Device E", 24, GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL),
                        16, 6, 2, 1920, 1080).quality());
    }

    private static WaterHardwareQualitySelector.Selection select(
            GPUCapabilities gpu,
            int processors,
            long physicalMemoryGiB,
            long heapGiB,
            int width,
            int height
    ) {
        return WaterHardwareQualitySelector.select(new WaterHardwareQualitySelector.HardwareProfile(
                gpu,
                processors,
                physicalMemoryGiB * GIB,
                heapGiB * GIB,
                width,
                height
        ));
    }

    private static GPUCapabilities modernGpu(
            String renderer,
            long videoMemoryGiB,
            GPUCapabilities.MemoryEvidence memoryEvidence
    ) {
        return new GPUCapabilities(
                GPUCapabilities.GraphicsApi.OPENGL,
                "diagnostic vendor",
                renderer,
                "4.6",
                videoMemoryGiB * GIB,
                memoryEvidence,
                16_384,
                8,
                Set.of(
                        GPUCapabilities.Feature.SHADER_PROGRAMS,
                        GPUCapabilities.Feature.FRAMEBUFFER_BLIT,
                        GPUCapabilities.Feature.DEPTH_TEXTURES,
                        GPUCapabilities.Feature.MULTIPLE_RENDER_TARGETS,
                        GPUCapabilities.Feature.GPU_TIMER_QUERIES,
                        GPUCapabilities.Feature.COMPUTE_SHADERS,
                        GPUCapabilities.Feature.IMAGE_LOAD_STORE,
                        GPUCapabilities.Feature.TEXTURE_STORAGE
                )
        );
    }

    private static GPUCapabilities basicGpu() {
        return new GPUCapabilities(
                GPUCapabilities.GraphicsApi.OPENGL,
                "diagnostic vendor",
                "basic device",
                "3.3",
                -1L,
                GPUCapabilities.MemoryEvidence.UNAVAILABLE,
                8_192,
                4,
                Set.of(
                        GPUCapabilities.Feature.SHADER_PROGRAMS,
                        GPUCapabilities.Feature.FRAMEBUFFER_BLIT,
                        GPUCapabilities.Feature.DEPTH_TEXTURES,
                        GPUCapabilities.Feature.MULTIPLE_RENDER_TARGETS
                )
        );
    }
}
