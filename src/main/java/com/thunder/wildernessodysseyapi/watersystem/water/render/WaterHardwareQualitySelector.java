package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;

import java.util.Locale;

/**
 * Selects a conservative water tier from immutable client hardware facts.
 *
 * <p>The weakest relevant component wins. This deliberately favors a stable
 * frame rate over promoting an unfamiliar GPU to a visually expensive tier.
 * No live Minecraft or OpenGL state is read here, keeping the policy directly
 * testable and separate from the client hardware probe.</p>
 */
final class WaterHardwareQualitySelector {

    private static final long GIBIBYTE = 1024L * 1024L * 1024L;
    private WaterHardwareQualitySelector() {
    }

    static Selection select(HardwareProfile profile) {
        CapabilityTier gpu = gpuTier(profile.gpuCapabilities());
        CapabilityTier cpu = cpuTier(profile.logicalProcessors());
        CapabilityTier memory = memoryTier(profile.physicalMemoryBytes(), profile.maximumHeapBytes());
        CapabilityTier resolution = resolutionTier(profile.framebufferWidth(), profile.framebufferHeight());
        CapabilityTier selected = minimum(gpu, cpu, memory, resolution);
        return new Selection(toWaterQuality(selected), gpu, cpu, memory, resolution);
    }

    private static CapabilityTier gpuTier(GPUCapabilities capabilities) {
        GPUCapabilities gpu = capabilities == null ? GPUCapabilities.UNAVAILABLE : capabilities;
        if (!gpu.available()
                || !gpu.supports(GPUCapabilities.Feature.FRAMEBUFFER_BLIT)
                || gpu.maximumTextureSize() < 4_096) {
            return CapabilityTier.LOW;
        }
        CapabilityTier featureTier = gpu.supportsComputeShaders()
                && gpu.supports(GPUCapabilities.Feature.IMAGE_LOAD_STORE)
                && gpu.supportsGpuTiming()
                && gpu.supportsHighQualityVolumetrics()
                ? CapabilityTier.CINEMATIC
                : gpu.supportsAdvancedReflections() && gpu.supportsHighQualityVolumetrics()
                ? CapabilityTier.HIGH
                : CapabilityTier.MEDIUM;
        return minimum(featureTier, videoMemoryTier(
                gpu.reportedVideoMemoryBytes(),
                gpu.memoryEvidence()
        ));
    }

    private static CapabilityTier videoMemoryTier(
            long bytes,
            GPUCapabilities.MemoryEvidence evidence
    ) {
        if (evidence == GPUCapabilities.MemoryEvidence.UNAVAILABLE || bytes <= 0L) {
            return CapabilityTier.MEDIUM;
        }
        if (bytes >= 10L * GIBIBYTE) {
            return evidence == GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL
                    ? CapabilityTier.CINEMATIC
                    : CapabilityTier.HIGH;
        }
        if (bytes >= 6L * GIBIBYTE) {
            return CapabilityTier.HIGH;
        }
        if (bytes >= 3L * GIBIBYTE) {
            return CapabilityTier.MEDIUM;
        }
        return CapabilityTier.LOW;
    }

    private static CapabilityTier cpuTier(int logicalProcessors) {
        if (logicalProcessors >= 12) {
            return CapabilityTier.CINEMATIC;
        }
        if (logicalProcessors >= 6) {
            return CapabilityTier.HIGH;
        }
        if (logicalProcessors >= 4) {
            return CapabilityTier.MEDIUM;
        }
        return CapabilityTier.LOW;
    }

    private static CapabilityTier memoryTier(long physicalBytes, long maximumHeapBytes) {
        CapabilityTier heap = maximumHeapBytes >= 6L * GIBIBYTE
                ? CapabilityTier.CINEMATIC
                : maximumHeapBytes >= 4L * GIBIBYTE
                ? CapabilityTier.HIGH
                : maximumHeapBytes >= 2L * GIBIBYTE
                ? CapabilityTier.MEDIUM
                : CapabilityTier.LOW;
        if (physicalBytes <= 0L) {
            return heap;
        }
        CapabilityTier physical = physicalBytes >= 24L * GIBIBYTE
                ? CapabilityTier.CINEMATIC
                : physicalBytes >= 16L * GIBIBYTE
                ? CapabilityTier.HIGH
                : physicalBytes >= 8L * GIBIBYTE
                ? CapabilityTier.MEDIUM
                : CapabilityTier.LOW;
        return minimum(heap, physical);
    }

    private static CapabilityTier resolutionTier(int width, int height) {
        if (width <= 0 || height <= 0) {
            return CapabilityTier.HIGH;
        }
        long pixels = (long) width * height;
        if (pixels > 8_500_000L) {
            return CapabilityTier.MEDIUM;
        }
        if (pixels > 3_800_000L) {
            return CapabilityTier.HIGH;
        }
        return CapabilityTier.CINEMATIC;
    }

    private static WaterRenderingConfig.WaterQuality toWaterQuality(CapabilityTier tier) {
        return switch (tier) {
            case LOW -> WaterRenderingConfig.WaterQuality.LOW;
            case MEDIUM -> WaterRenderingConfig.WaterQuality.MEDIUM;
            case HIGH -> WaterRenderingConfig.WaterQuality.HIGH;
            case CINEMATIC -> WaterRenderingConfig.WaterQuality.CINEMATIC;
        };
    }

    private static CapabilityTier minimum(CapabilityTier... tiers) {
        CapabilityTier result = CapabilityTier.CINEMATIC;
        for (CapabilityTier tier : tiers) {
            if (tier.ordinal() < result.ordinal()) {
                result = tier;
            }
        }
        return result;
    }

    record HardwareProfile(
            GPUCapabilities gpuCapabilities,
            int logicalProcessors,
            long physicalMemoryBytes,
            long maximumHeapBytes,
            int framebufferWidth,
            int framebufferHeight
    ) {
    }

    record Selection(
            WaterRenderingConfig.WaterQuality quality,
            CapabilityTier gpuTier,
            CapabilityTier cpuTier,
            CapabilityTier memoryTier,
            CapabilityTier resolutionTier
    ) {
        String summary() {
            return "GPU " + label(gpuTier)
                    + " / CPU " + label(cpuTier)
                    + " / memory " + label(memoryTier)
                    + " / display " + label(resolutionTier);
        }

        private static String label(CapabilityTier tier) {
            return tier.name().toLowerCase(Locale.ROOT);
        }
    }

    enum CapabilityTier {
        LOW,
        MEDIUM,
        HIGH,
        CINEMATIC
    }
}
