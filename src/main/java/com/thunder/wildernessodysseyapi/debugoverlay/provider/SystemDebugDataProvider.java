package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.mojang.blaze3d.platform.GlUtil;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.List;

/** Collects cached runtime/hardware identity plus the current JVM heap state. */
public final class SystemDebugDataProvider implements DebugDataProvider {
    private SystemSnapshot snapshot;

    @Override
    public List<DebugSection> collect(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        SystemSnapshot staticInfo = snapshot();
        Runtime runtime = Runtime.getRuntime();
        long allocated = runtime.totalMemory();
        long used = allocated - runtime.freeMemory();

        return List.of(
                DebugSection.builder("GAME")
                        .add("Minecraft", SharedConstants.getCurrentVersion().getName())
                        .add("Launched version", minecraft.getLaunchedVersion())
                        .add("Client brand", ClientBrandRetriever.getClientModName())
                        .add("NeoForge", staticInfo.neoForgeVersion())
                        .build(),
                DebugSection.builder("JAVA")
                        .add("Runtime", staticInfo.javaVersion())
                        .add("JVM", staticInfo.jvm())
                        .add("Used heap", PerformanceDebugDataProvider.formatBytes(used))
                        .add("Allocated heap", PerformanceDebugDataProvider.formatBytes(allocated))
                        .add("Maximum heap", PerformanceDebugDataProvider.formatBytes(runtime.maxMemory()))
                        .build(),
                DebugSection.builder("SYSTEM")
                        .add("Operating system", staticInfo.operatingSystem())
                        .add("CPU", staticInfo.cpu())
                        .add("GPU", staticInfo.gpu())
                        .add("OpenGL", staticInfo.openGl())
                        .build(),
                DebugSection.builder("DISPLAY")
                        .add("Window", minecraft.getWindow().getWidth() + " x " + minecraft.getWindow().getHeight())
                        .add("GUI scale", String.format("%.2f", minecraft.getWindow().getGuiScale()))
                        .add("GUI resolution", minecraft.getWindow().getGuiScaledWidth() + " x " + minecraft.getWindow().getGuiScaledHeight())
                        .add("Fullscreen", minecraft.getWindow().isFullscreen() ? "Yes" : "No")
                        .build()
        );
    }

    private SystemSnapshot snapshot() {
        if (snapshot != null) {
            return snapshot;
        }

        String neoForge = ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unavailable");
        String cpu;
        String gpu;
        String openGl;
        try {
            cpu = available(GlUtil.getCpuInfo());
            gpu = available(GlUtil.getRenderer());
            openGl = available(GlUtil.getOpenGLVersion());
        } catch (RuntimeException exception) {
            cpu = "Unavailable";
            gpu = "Unavailable";
            openGl = "Unavailable";
        }

        snapshot = new SystemSnapshot(
                neoForge,
                property("java.version"),
                property("java.vm.name") + " " + property("java.vm.version"),
                property("os.name") + " " + property("os.version") + " (" + property("os.arch") + ")",
                cpu,
                gpu,
                openGl
        );
        return snapshot;
    }

    private static String property(String key) {
        return System.getProperty(key, "Unavailable");
    }

    private static String available(String value) {
        return value == null || value.isBlank() ? "Unavailable" : value;
    }

    private record SystemSnapshot(
            String neoForgeVersion,
            String javaVersion,
            String jvm,
            String operatingSystem,
            String cpu,
            String gpu,
            String openGl
    ) {
    }
}
