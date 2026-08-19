package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngineSnapshot;
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
        DebugSection tickEngine = tickEngineSection(minecraft);

        if (minecraft.level == null) {
            return List.of(frame, memory, tickEngine, DebugSection.builder("WORLD LOAD")
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
        return List.of(frame, memory, tickEngine, worldLoad);
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

    private static DebugSection tickEngineSection(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) {
            return DebugSection.builder("WO TICK ENGINE")
                    .add("State", DebugValue.unavailable("Remote metrics are not synchronized"))
                    .build();
        }
        TickEngineSnapshot snapshot = TickEngine.snapshot();
        DebugValue pressure = switch (snapshot.pressure()) {
            case RELAXED -> DebugValue.good(snapshot.pressure());
            case BUSY, HIGH -> DebugValue.warning(snapshot.pressure());
            case CRITICAL, OVERLOADED -> DebugValue.error(snapshot.pressure());
        };
        return DebugSection.builder("WO TICK ENGINE")
                .add("State", snapshot.enabled() ? DebugValue.good("Enabled") : DebugValue.unavailable("Disabled"))
                .add("TPS", String.format(Locale.ROOT, "%.2f", snapshot.tps()))
                .add("Current MSPT", String.format(Locale.ROOT, "%.2f ms", snapshot.currentMspt()))
                .add("Short average", String.format(Locale.ROOT, "%.2f ms", snapshot.shortAverageMspt()))
                .add("Medium average", String.format(Locale.ROOT, "%.2f ms", snapshot.mediumAverageMspt()))
                .add("Pressure", pressure)
                .add("WO budget used", String.format(Locale.ROOT, "%.3f / %.3f ms",
                        snapshot.optionalWorkMillis(), snapshot.optionalBudgetMillis()))
                .add("Deferred work", snapshot.deferredTasks() + snapshot.backgroundQueuedTasks())
                .add("Throttled systems", snapshot.throttledSubsystems())
                .add("Worst WO subsystem", snapshot.worstSubsystem())
                .build();
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0D * 1024.0D));
        }
        return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }
}
