package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/** Collects cheap frame, heap, chunk, entity, and particle counters. */
public final class PerformanceDebugDataProvider implements DebugDataProvider {
    @Override
    public List<DebugSection> collect(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long allocated = runtime.totalMemory();
        long used = allocated - runtime.freeMemory();
        int usedPercent = max <= 0L ? 0 : (int) (used * 100L / max);

        DebugSection frame = frameSection(minecraft);
        DebugSection memory = DebugSection.builder("MEMORY")
                .add("Used", memoryTone(usedPercent, formatBytes(used) + " / " + formatBytes(max) + " (" + usedPercent + "%)"))
                .add("JVM allocated", formatBytes(allocated))
                .add("Maximum heap", formatBytes(max))
                .build();

        if (minecraft.level == null) {
            return List.of(frame, memory, DebugSection.builder("WORLD LOAD")
                    .add("State", DebugValue.unavailable("No world loaded"))
                    .build());
        }

        DebugSection worldLoad = DebugSection.builder("WORLD LOAD")
                .add("Rendered chunks", minecraft.levelRenderer.countRenderedSections())
                .add("Loaded chunks", minecraft.level.getChunkSource().getLoadedChunksCount())
                .add("Render distance", minecraft.options.getEffectiveRenderDistance() + " chunks")
                .add("Simulation distance", minecraft.options.simulationDistance().get() + " chunks")
                .add("Entities", minecraft.levelRenderer.getEntityStatistics())
                .add("Client entity count", minecraft.level.getEntityCount())
                .add("Particles", minecraft.particleEngine.countParticles())
                .add("Chunk renderer", minecraft.levelRenderer.getSectionStatistics())
                .build();
        return List.of(frame, memory, worldLoad);
    }

    /** Returns the compact FPS/frame-time section used by the General page. */
    public List<DebugSection> summary(DebugContext context) {
        return List.of(frameSection(context.minecraft()));
    }

    private static DebugSection frameSection(Minecraft minecraft) {
        int fps = minecraft.getFps();
        double frameMillis = minecraft.getFrameTimeNs() / 1_000_000.0D;
        DebugValue frameTone = frameMillis <= 16.7D
                ? DebugValue.good(String.format(Locale.ROOT, "%.2f ms", frameMillis))
                : frameMillis <= 33.4D
                ? DebugValue.warning(String.format(Locale.ROOT, "%.2f ms", frameMillis))
                : DebugValue.error(String.format(Locale.ROOT, "%.2f ms", frameMillis));
        return DebugSection.builder("PERFORMANCE")
                .add("FPS", fps)
                .add("Frame time", frameTone)
                .build();
    }

    private static DebugValue memoryTone(int percent, String text) {
        if (percent >= 90) {
            return DebugValue.error(text);
        }
        if (percent >= 75) {
            return DebugValue.warning(text);
        }
        return DebugValue.normal(text);
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0D * 1024.0D));
        }
        return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }
}
