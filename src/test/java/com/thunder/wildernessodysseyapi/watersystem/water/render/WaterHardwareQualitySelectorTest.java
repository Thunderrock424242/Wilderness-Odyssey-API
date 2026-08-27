package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies conservative automatic water-quality selection across common hardware classes. */
class WaterHardwareQualitySelectorTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    void highEndBalancedSystemSelectsCinematic() {
        var selection = select("NVIDIA GeForce RTX 4090", 24, 24, 32, 8, 2560, 1440);

        assertEquals(WaterRenderingConfig.WaterQuality.CINEMATIC, selection.quality());
        assertTrue(selection.summary().contains("GPU cinematic"));
    }

    @Test
    void limitedMinecraftHeapCapsOtherwiseHighEndSystem() {
        var selection = select("NVIDIA GeForce RTX 4090", 24, 24, 32, 4, 2560, 1440);

        assertEquals(WaterRenderingConfig.WaterQuality.HIGH, selection.quality());
    }

    @Test
    void mainstreamDedicatedGpuSelectsHighInsteadOfCinematic() {
        var selection = select("NVIDIA GeForce RTX 3060", 12, 16, 32, 8, 1920, 1080);

        assertEquals(WaterRenderingConfig.WaterQuality.HIGH, selection.quality());
    }

    @Test
    void intelArcDriverNameIsRecognized() {
        var selection = select("Intel(R) Arc(TM) A770 Graphics", 16, 16, 32, 8, 1920, 1080);

        assertEquals(WaterRenderingConfig.WaterQuality.HIGH, selection.quality());
    }

    @Test
    void integratedAndSoftwareRenderersRemainConservative() {
        assertEquals(WaterRenderingConfig.WaterQuality.MEDIUM,
                select("AMD Radeon 780M Graphics", -1, 16, 32, 8, 1920, 1080).quality());
        assertEquals(WaterRenderingConfig.WaterQuality.MEDIUM,
                select("AMD Radeon RX 580 Series", 8, 16, 32, 8, 1920, 1080).quality());
        assertEquals(WaterRenderingConfig.WaterQuality.LOW,
                select("Microsoft Basic Render Driver", -1, 24, 64, 12, 1920, 1080).quality());
    }

    @Test
    void ultrawideOrFourKDisplayCapsCinematicAtHigh() {
        assertEquals(WaterRenderingConfig.WaterQuality.HIGH,
                select("AMD Radeon RX 7900 XTX", 24, 24, 32, 8, 3840, 2160).quality());
    }

    @Test
    void cpuAndRamCanCapAnExpensiveGpu() {
        assertEquals(WaterRenderingConfig.WaterQuality.MEDIUM,
                select("NVIDIA GeForce RTX 4090", 24, 4, 16, 4, 1920, 1080).quality());
        assertEquals(WaterRenderingConfig.WaterQuality.LOW,
                select("NVIDIA GeForce RTX 4090", 24, 16, 6, 2, 1920, 1080).quality());
    }

    private static WaterHardwareQualitySelector.Selection select(
            String renderer,
            long videoMemoryGiB,
            int processors,
            long physicalMemoryGiB,
            long heapGiB,
            int width,
            int height
    ) {
        return WaterHardwareQualitySelector.select(new WaterHardwareQualitySelector.HardwareProfile(
                renderer,
                videoMemoryGiB < 0 ? -1L : videoMemoryGiB * GIB,
                processors,
                physicalMemoryGiB * GIB,
                heapGiB * GIB,
                width,
                height
        ));
    }
}
