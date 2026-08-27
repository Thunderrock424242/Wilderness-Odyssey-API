package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.platform.GlUtil;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuHardwareProbe;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.management.ManagementFactory;

/**
 * Detects client hardware once the OpenGL context is ready and publishes the
 * effective automatic water quality to {@link WaterRenderingConfig}.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WaterHardwareAutoDetector {

    private static final int MAX_GPU_PROBE_ATTEMPTS = 100;

    private static boolean detected;
    private static int probeAttempts;

    private WaterHardwareAutoDetector() {
    }

    /** Performs one bounded startup probe on the client/render thread. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (detected || !WaterRenderingConfig.automaticallyDetectWaterQuality()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        GpuHardwareProbe.Snapshot gpu = GpuHardwareProbe.capture();
        if (!gpu.rendererAvailable() && ++probeAttempts < MAX_GPU_PROBE_ATTEMPTS) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long physicalMemory = totalPhysicalMemoryBytes();
        WaterHardwareQualitySelector.HardwareProfile hardware =
                new WaterHardwareQualitySelector.HardwareProfile(
                        gpu.renderer(),
                        gpu.reportedVideoMemoryBytes(),
                        runtime.availableProcessors(),
                        physicalMemory,
                        runtime.maxMemory(),
                        width,
                        height
                );
        WaterHardwareQualitySelector.Selection selection =
                WaterHardwareQualitySelector.select(hardware);
        WaterRenderingConfig.WaterQuality previousQuality = WaterRenderingConfig.waterQuality();
        WaterRenderingConfig.applyAutoDetectedWaterQuality(
                selection.quality(),
                selection.summary()
        );
        if (previousQuality != selection.quality()) {
            FluidRenderer.clear();
            if (minecraft.level != null) {
                ClientWaterSnapshotStore.markAllDirtyMeshes(minecraft.level);
            }
        }
        detected = true;

        ModConstants.LOGGER.info(
                "[Water] Automatic quality selected {} ({}) from GPU '{}', CPU '{}', {} logical processors, physical RAM {}, max heap {}, framebuffer {}x{}.",
                selection.quality(),
                selection.summary(),
                available(gpu.renderer()),
                cpuDescription(),
                runtime.availableProcessors(),
                formatMemory(physicalMemory),
                formatMemory(runtime.maxMemory()),
                width,
                height
        );
    }

    private static long totalPhysicalMemoryBytes() {
        try {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                return Math.max(-1L, extended.getTotalMemorySize());
            }
        } catch (Throwable ignored) {
            // The JVM heap remains a safe conservative fallback for selection.
        }
        return -1L;
    }

    private static String cpuDescription() {
        try {
            return available(GlUtil.getCpuInfo());
        } catch (RuntimeException unavailable) {
            return "unavailable";
        }
    }

    private static String available(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }

    private static String formatMemory(long bytes) {
        if (bytes <= 0L) {
            return "unavailable";
        }
        return String.format("%.1f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
