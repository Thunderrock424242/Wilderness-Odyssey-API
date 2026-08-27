package com.thunder.wildernessodysseyapi.watersystem.water.render;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern FOUR_DIGIT_MODEL = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final Pattern INTEL_ARC_MODEL = Pattern.compile(
            "\\barc(?:\\(tm\\))?\\s+([ab])(\\d{3})\\b"
    );

    private WaterHardwareQualitySelector() {
    }

    static Selection select(HardwareProfile profile) {
        CapabilityTier gpu = gpuTier(profile.gpuRenderer(), profile.reportedVideoMemoryBytes());
        CapabilityTier cpu = cpuTier(profile.logicalProcessors());
        CapabilityTier memory = memoryTier(profile.physicalMemoryBytes(), profile.maximumHeapBytes());
        CapabilityTier resolution = resolutionTier(profile.framebufferWidth(), profile.framebufferHeight());
        CapabilityTier selected = minimum(gpu, cpu, memory, resolution);
        return new Selection(toWaterQuality(selected), gpu, cpu, memory, resolution);
    }

    private static CapabilityTier gpuTier(String renderer, long videoMemoryBytes) {
        String normalized = normalize(renderer);
        if (normalized.isEmpty() || normalized.equals("unavailable")) {
            return CapabilityTier.LOW;
        }
        if (containsAny(normalized, "llvmpipe", "swiftshader", "software rasterizer",
                "microsoft basic render", "gdi generic")) {
            return CapabilityTier.LOW;
        }

        // Shared-memory graphics must not be promoted by a misleading driver
        // memory report. Newer integrated parts retain the balanced tier.
        if (containsAny(normalized, "intel hd graphics", "intel(r) hd graphics",
                "intel uhd graphics", "intel(r) uhd graphics", "geforce mx")) {
            return CapabilityTier.LOW;
        }
        if (containsAny(normalized, "iris xe", "iris plus", "radeon 780m", "radeon 760m",
                "radeon 680m", "radeon vega", "radeon(tm) graphics")) {
            return CapabilityTier.MEDIUM;
        }

        CapabilityTier namedTier = namedDiscreteGpuTier(normalized);
        CapabilityTier memoryTier = videoMemoryTier(videoMemoryBytes);
        if (namedTier != null) {
            return videoMemoryBytes > 0L ? minimum(namedTier, memoryTier) : namedTier;
        }
        if (videoMemoryBytes > 0L) {
            // Unknown workstation/future devices may use their reported VRAM,
            // but never auto-promote past HIGH without a recognized family.
            return minimum(CapabilityTier.HIGH, memoryTier);
        }
        return CapabilityTier.MEDIUM;
    }

    private static CapabilityTier namedDiscreteGpuTier(String renderer) {
        if (renderer.contains("nvidia") || renderer.contains("geforce")) {
            if (renderer.contains(" rtx ") || renderer.startsWith("rtx ")) {
                int model = firstFourDigitModel(renderer);
                if (model < 0) {
                    return CapabilityTier.HIGH;
                }
                int generation = model / 1000;
                int modelClass = model % 1000;
                if ((generation >= 3 && modelClass >= 80)
                        || (generation >= 4 && modelClass >= 70)) {
                    return CapabilityTier.CINEMATIC;
                }
                return modelClass >= 60 ? CapabilityTier.HIGH : CapabilityTier.MEDIUM;
            }
            if (renderer.contains(" gtx ") || renderer.startsWith("gtx ")) {
                return CapabilityTier.MEDIUM;
            }
            return null;
        }

        if (renderer.contains("radeon rx")) {
            int model = firstFourDigitModel(renderer);
            if (model < 0) {
                // Older three-digit RX cards and unrecognized names should
                // stay balanced unless a future family is explicitly ranked.
                return CapabilityTier.MEDIUM;
            }
            int generation = model / 1000;
            int modelClass = model % 1000;
            if ((generation >= 6 && generation <= 8 && modelClass >= 800)
                    || (generation >= 9 && modelClass >= 70)) {
                return CapabilityTier.CINEMATIC;
            }
            if ((generation >= 5 && modelClass >= 600)
                    || (generation >= 9 && modelClass >= 60)) {
                return CapabilityTier.HIGH;
            }
            return CapabilityTier.MEDIUM;
        }

        Matcher arc = INTEL_ARC_MODEL.matcher(renderer);
        if (arc.find()) {
            char family = arc.group(1).charAt(0);
            int model = Integer.parseInt(arc.group(2));
            if ((family == 'a' && model >= 750) || (family == 'b' && model >= 570)) {
                return CapabilityTier.HIGH;
            }
            return CapabilityTier.MEDIUM;
        }

        if (renderer.contains("apple m")) {
            if (renderer.contains(" max")) {
                return CapabilityTier.CINEMATIC;
            }
            return renderer.contains(" pro") ? CapabilityTier.HIGH : CapabilityTier.MEDIUM;
        }
        return null;
    }

    private static CapabilityTier videoMemoryTier(long bytes) {
        if (bytes >= 10L * GIBIBYTE) {
            return CapabilityTier.CINEMATIC;
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

    private static int firstFourDigitModel(String value) {
        Matcher matcher = FOUR_DIGIT_MODEL.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record HardwareProfile(
            String gpuRenderer,
            long reportedVideoMemoryBytes,
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
