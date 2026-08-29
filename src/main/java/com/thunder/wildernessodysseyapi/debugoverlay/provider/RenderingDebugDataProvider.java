package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugPageContributorRegistry;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfiler;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackends;
import com.thunder.wildernessodysseyapi.rendering.client.WildernessRenderingFramework;
import com.thunder.wildernessodysseyapi.ecosystem.distant.client.ClientDistantWildlifeState;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderDiagnostics;
import com.thunder.wildernessodysseyapi.weather.client.WeatherClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Collects Minecraft/Blaze3D renderer state and optional integration sections. */
public final class RenderingDebugDataProvider implements DebugDataProvider {
    public static final ResourceLocation PAGE_ID = ResourceLocation.fromNamespaceAndPath(
            "wildernessodysseyapi", "rendering"
    );

    private GraphicsSnapshot graphicsSnapshot;

    @Override
    public List<DebugSection> collect(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        List<DebugSection> sections = new ArrayList<>();

        if (minecraft.level == null) {
            sections.add(DebugSection.builder("RENDERER")
                    .add("World renderer", DebugValue.unavailable("No world loaded"))
                    .build());
        } else {
            sections.add(DebugSection.builder("WORLD RENDERER")
                    .add("Sections", minecraft.levelRenderer.getSectionStatistics())
                    .add("Rendered chunks", minecraft.levelRenderer.countRenderedSections())
                    .add("Entities", minecraft.levelRenderer.getEntityStatistics())
                    .add("Render distance", minecraft.options.getEffectiveRenderDistance() + " chunks")
                    .add("Simulation distance", minecraft.options.simulationDistance().get() + " chunks")
                    .build());
        }

        GraphicsSnapshot graphics = graphicsSnapshot();
        sections.add(DebugSection.builder("GRAPHICS")
                .add("GPU", graphics.renderer())
                .add("Vendor", graphics.vendor())
                .add("OpenGL", graphics.openGlVersion())
                .add("Backend", graphics.backend())
                .add("Graphics mode", minecraft.options.graphicsMode().get())
                .add("VSync", minecraft.options.enableVsync().get()
                        ? DebugValue.good("Enabled")
                        : DebugValue.normal("Disabled"))
                .build());

        RenderTarget target = minecraft.getMainRenderTarget();
        sections.add(DebugSection.builder("DISPLAY & FRAMEBUFFER")
                .add("Display", minecraft.getWindow().getWidth() + " x " + minecraft.getWindow().getHeight())
                .add("GUI", minecraft.getWindow().getGuiScaledWidth() + " x " + minecraft.getWindow().getGuiScaledHeight())
                .add("Framebuffer", target.width + " x " + target.height)
                .add("Viewport", target.viewWidth + " x " + target.viewHeight)
                .add("Framebuffer ID", target.frameBufferId)
                .add("Depth", target.useDepth ? "Enabled" : "Disabled")
                .build());

        // These diagnostics previously lived in the unbounded vanilla right
        // column. Keeping them here preserves the information while limiting
        // their collection to the cached Rendering page refresh.
        addRawSection(sections, "WILDERNESS RENDERING", WildernessRenderingFramework.debugLines());
        addRawSection(sections, "WILDERNESS GPU PROFILER", GpuProfiler.debugLines());
        addRawSection(sections, "WILDERNESS WATER", WaterRenderDiagnostics.debugLines());
        addRawSection(sections, "WILDERNESS WEATHER", WeatherClientEvents.debugLines());
        addRawSection(sections, "DISTANT WILDLIFE", ClientDistantWildlifeState.debugLines());

        List<DebugSection> contributedSections = DebugPageContributorRegistry.collect(PAGE_ID, context);
        sections.addAll(contributedSections);
        if (contributedSections.isEmpty()) {
            sections.add(DebugSection.builder("OPTIONAL INTEGRATIONS")
                    .add("Iris / shader packs", DebugValue.unavailable("No contributor registered"))
                    .add("Distant Horizons / Voxy", DebugValue.unavailable("No contributor registered"))
                    .add("Wilderness water", DebugValue.unavailable("No contributor registered"))
                    .build());
        }
        return List.copyOf(sections);
    }

    private static void addRawSection(List<DebugSection> sections, String title, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        DebugSection.Builder section = DebugSection.builder(title);
        lines.forEach(section::addRaw);
        sections.add(section.build());
    }

    private GraphicsSnapshot graphicsSnapshot() {
        if (graphicsSnapshot != null) {
            return graphicsSnapshot;
        }
        try {
            var capabilities = RenderBackends.current().capabilities();
            graphicsSnapshot = new GraphicsSnapshot(
                    available(capabilities.renderer()),
                    available(capabilities.vendor()),
                    available(capabilities.driverVersion()),
                    "Blaze3D / " + capabilities.api()
            );
        } catch (RuntimeException exception) {
            graphicsSnapshot = new GraphicsSnapshot(
                    "Unavailable", "Unavailable", "Unavailable", "Unavailable"
            );
        }
        return graphicsSnapshot;
    }

    private static String available(String value) {
        return value == null || value.isBlank() ? "Unavailable" : value;
    }

    private record GraphicsSnapshot(String renderer, String vendor, String openGlVersion, String backend) {
    }
}
