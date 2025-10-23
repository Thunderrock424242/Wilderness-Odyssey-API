package com.thunder.wildernessodysseyapi.hardware.client.hardware;

import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the client's hardware information and compares it with the configured requirements.
 */
public final class HardwareRequirementChecker {
    private static final Logger LOGGER = LogManager.getLogger("HardwareRequirementChecker");
    private static final int GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;

    private final HardwareRequirementConfig config;
    private HardwareSnapshot snapshot;

    public HardwareRequirementChecker(HardwareRequirementConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public HardwareRequirementConfig config() {
        return config;
    }

    public HardwareSnapshot refresh() {
        this.snapshot = captureSnapshot();
        return this.snapshot;
    }

    public HardwareSnapshot getSnapshot() {
        return snapshot == null ? refresh() : snapshot;
    }

    public EnumMap<HardwareRequirementConfig.Tier, TierEvaluation> evaluateAll(HardwareSnapshot snapshot) {
        EnumMap<HardwareRequirementConfig.Tier, TierEvaluation> evaluations = new EnumMap<>(HardwareRequirementConfig.Tier.class);
        for (Map.Entry<HardwareRequirementConfig.Tier, HardwareRequirementConfig.HardwareRequirementTier> entry : config.getTiers().entrySet()) {
            evaluations.put(entry.getKey(), evaluateTier(snapshot, entry.getValue()));
        }
        return evaluations;
    }

    public EnumMap<HardwareRequirementConfig.Tier, TierEvaluation> evaluateAll() {
        return evaluateAll(getSnapshot());
    }

    public Optional<HardwareRequirementConfig.Tier> selectHighestMeetingTier(
        EnumMap<HardwareRequirementConfig.Tier, TierEvaluation> evaluations) {
        HardwareRequirementConfig.Tier[] tiers = HardwareRequirementConfig.Tier.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            HardwareRequirementConfig.Tier tier = tiers[i];
            TierEvaluation evaluation = evaluations.get(tier);
            if (evaluation != null && evaluation.meets()) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    private TierEvaluation evaluateTier(HardwareSnapshot snapshot, HardwareRequirementConfig.HardwareRequirementTier tier) {
        EnumSet<Metric> failing = EnumSet.noneOf(Metric.class);
        EnumSet<Metric> unknown = EnumSet.noneOf(Metric.class);

        if (tier.minCpuCores() > 0) {
            if (snapshot.cpuCores() <= 0) {
                unknown.add(Metric.CPU);
            } else if (snapshot.cpuCores() < tier.minCpuCores()) {
                failing.add(Metric.CPU);
            }
        }

        if (tier.minRamMb() > 0) {
            if (snapshot.systemRamMb() <= 0) {
                unknown.add(Metric.RAM);
            } else if (snapshot.systemRamMb() < tier.minRamMb()) {
                failing.add(Metric.RAM);
            }
        }

        if (tier.minVramMb() > 0) {
            if (snapshot.vramMb() <= 0) {
                unknown.add(Metric.VRAM);
            } else if (snapshot.vramMb() < tier.minVramMb()) {
                failing.add(Metric.VRAM);
            }
        }

        if (tier.hasGpuRequirement()) {
            if (snapshot.gpuVendor() == null && snapshot.gpuRenderer() == null) {
                unknown.add(Metric.GPU);
            } else if (!matchesGpuRequirement(snapshot, tier)) {
                failing.add(Metric.GPU);
            }
        }

        boolean meets = failing.isEmpty();
        return new TierEvaluation(tier.tier(), meets,
            failing.isEmpty() ? EnumSet.noneOf(Metric.class) : EnumSet.copyOf(failing),
            unknown.isEmpty() ? EnumSet.noneOf(Metric.class) : EnumSet.copyOf(unknown));
    }

    private boolean matchesGpuRequirement(HardwareSnapshot snapshot, HardwareRequirementConfig.HardwareRequirementTier tier) {
        String vendor = snapshot.gpuVendor();
        String renderer = snapshot.gpuRenderer();
        if (vendor == null && renderer == null) {
            return false;
        }

        String vendorLower = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
        String rendererLower = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);

        boolean vendorMatch = tier.gpuVendorKeywords().isEmpty() || tier.gpuVendorKeywords().stream().anyMatch(vendorLower::contains);
        boolean rendererMatch = tier.gpuRendererKeywords().isEmpty() || tier.gpuRendererKeywords().stream().anyMatch(rendererLower::contains);
        return vendorMatch && rendererMatch;
    }

    private HardwareSnapshot captureSnapshot() {
        String cpuName = queryCpuName();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        long physicalRamMb = querySystemRamMb();
        long vramMb = queryVramMb();

        String gpuVendor = queryGlString(GL11.GL_VENDOR);
        String gpuRenderer = queryGlString(GL11.GL_RENDERER);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Detected hardware: CPU='{}' ({} cores), RAM={} MB, VRAM={} MB, GPU vendor='{}', renderer='{}'",
                cpuName, cpuCores, physicalRamMb, vramMb, gpuVendor, gpuRenderer);
        }

        return new HardwareSnapshot(cpuName, cpuCores, physicalRamMb, vramMb, gpuVendor, gpuRenderer);
    }

    private static String queryCpuName() {
        String env = System.getenv("PROCESSOR_IDENTIFIER");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String cpu = System.getenv("CPU");
        if (cpu != null && !cpu.isBlank()) {
            return cpu;
        }
        return System.getProperty("os.arch", "unknown");
    }

    private static long querySystemRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalBytes = osBean.getTotalMemorySize();
            return totalBytes > 0 ? totalBytes / (1024L * 1024L) : -1L;
        } catch (ClassCastException | LinkageError ex) {
            long maxMemory = Runtime.getRuntime().maxMemory();
            return maxMemory > 0 ? maxMemory / (1024L * 1024L) : -1L;
        }
    }

    private static long queryVramMb() {
        try {
            GLCapabilities capabilities = GL.getCapabilities();
            if (capabilities == null) {
                return -1L;
            }

            if (capabilities.GL_NVX_gpu_memory_info) {
                int totalKb = GL11.glGetInteger(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX);
                return Mth.floor(totalKb / 1024f);
            }
        } catch (IllegalStateException ex) {
            LOGGER.debug("Unable to query VRAM: OpenGL context not ready");
        }
        return -1L;
    }

    private static String queryGlString(int target) {
        try {
            String value = GL11.glGetString(target);
            return value != null && !value.isBlank() ? value : null;
        } catch (IllegalStateException ex) {
            LOGGER.debug("Unable to query OpenGL string {}", target);
            return null;
        }
    }

    /**
     * Snapshot of the hardware information currently known by the checker.
     */
    public record HardwareSnapshot(String cpuName, int cpuCores, long systemRamMb, long vramMb,
                                   String gpuVendor, String gpuRenderer) {
    }

    /**
     * Evaluation result for a given tier.
     */
    public record TierEvaluation(HardwareRequirementConfig.Tier tier, boolean meets,
                                 EnumSet<Metric> failingMetrics, EnumSet<Metric> unknownMetrics) {
        public boolean hasUnknownMetrics() {
            return !unknownMetrics.isEmpty();
        }
    }

    public enum Metric {
        CPU("hardware.wildernessodyssey.metric.cpu"),
        RAM("hardware.wildernessodyssey.metric.ram"),
        VRAM("hardware.wildernessodyssey.metric.vram"),
        GPU("hardware.wildernessodyssey.metric.gpu");

        private final String translationKey;

        Metric(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }
}
