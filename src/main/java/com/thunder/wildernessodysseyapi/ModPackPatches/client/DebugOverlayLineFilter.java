package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.wildernessodysseyapi.config.DebugOverlayConfig;

import java.util.ArrayList;
import java.util.List;

public final class DebugOverlayLineFilter {
    private static final List<LineToggle> GAME_LINE_TOGGLES = List.of(
            new LineToggle(DebugOverlayConfig.SHOW_HELP_LINE, "For help:"),
            new LineToggle(DebugOverlayConfig.SHOW_VERSION_LINE, "Minecraft"),
            new LineToggle(DebugOverlayConfig.SHOW_FPS_LINE, "FPS:"),
            new LineToggle(DebugOverlayConfig.SHOW_ENTITY_LINE, "E:"),
            new LineToggle(DebugOverlayConfig.SHOW_XYZ_LINE, "XYZ:"),
            new LineToggle(DebugOverlayConfig.SHOW_BLOCK_LINE, "Block:"),
            new LineToggle(DebugOverlayConfig.SHOW_CHUNK_LINE, "Chunk:"),
            new LineToggle(DebugOverlayConfig.SHOW_CHUNK_LINE, "Chunk-relative:"),
            new LineToggle(DebugOverlayConfig.SHOW_FACING_LINE, "Facing:"),
            new LineToggle(DebugOverlayConfig.SHOW_LIGHT_LINE, "Light:"),
            new LineToggle(DebugOverlayConfig.SHOW_LIGHT_LINE, "Client Light:"),
            new LineToggle(DebugOverlayConfig.SHOW_LIGHT_LINE, "Server Light:"),
            new LineToggle(DebugOverlayConfig.SHOW_BIOME_LINE, "Biome:"),
            new LineToggle(DebugOverlayConfig.SHOW_DIFFICULTY_LINE, "Difficulty:"),
            new LineToggle(DebugOverlayConfig.SHOW_LOCAL_DIFFICULTY_LINE, "Local Difficulty:"),
            new LineToggle(DebugOverlayConfig.SHOW_DAYTIME_LINE, "Daytime:"),
            new LineToggle(DebugOverlayConfig.SHOW_DAYTIME_LINE, "Day:")
    );

    private static final List<LineToggle> SYSTEM_LINE_TOGGLES = List.of(
            new LineToggle(DebugOverlayConfig.SHOW_JAVA_LINE, "Java:"),
            new LineToggle(DebugOverlayConfig.SHOW_MEMORY_LINE, "Mem:"),
            new LineToggle(DebugOverlayConfig.SHOW_MEMORY_LINE, "Allocated:"),
            new LineToggle(DebugOverlayConfig.SHOW_ALLOCATION_LINE, "Allocation rate:"),
            new LineToggle(DebugOverlayConfig.SHOW_CPU_LINE, "CPU:"),
            new LineToggle(DebugOverlayConfig.SHOW_DISPLAY_LINE, "Display:"),
            new LineToggle(DebugOverlayConfig.SHOW_GPU_LINE, "GPU:"),
            new LineToggle(DebugOverlayConfig.SHOW_OPENGL_LINE, "OpenGL:"),
            new LineToggle(DebugOverlayConfig.SHOW_RENDERER_LINE, "Renderer:"),
            new LineToggle(DebugOverlayConfig.SHOW_SERVER_LINE, "Server:"),
            new LineToggle(DebugOverlayConfig.SHOW_SERVER_LINE, "Integrated server")
    );

    private DebugOverlayLineFilter() {
    }

    public static List<String> filterGameLines(List<String> lines) {
        return filterLines(lines, GAME_LINE_TOGGLES, true);
    }

    public static List<String> filterSystemLines(List<String> lines) {
        return filterLines(lines, SYSTEM_LINE_TOGGLES, false);
    }

    private static List<String> filterLines(List<String> lines, List<LineToggle> toggles, boolean handleTargets) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }

        List<String> filtered = new ArrayList<>(lines.size());
        boolean skippingTargetGroup = false;

        for (String line : lines) {
            if (handleTargets) {
                TargetSection section = TargetSection.fromLine(line);
                if (section != TargetSection.NONE) {
                    skippingTargetGroup = !section.isEnabled();
                    if (!skippingTargetGroup) {
                        filtered.add(line);
                    }
                    continue;
                }

                if (skippingTargetGroup) {
                    if (isTargetContinuation(line)) {
                        continue;
                    }
                    skippingTargetGroup = false;
                }
            }

            if (shouldSkip(line, toggles)) {
                continue;
            }
            filtered.add(line);
        }

        return filtered;
    }

    private static boolean shouldSkip(String line, List<LineToggle> toggles) {
        if (line == null) {
            return false;
        }
        for (LineToggle toggle : toggles) {
            if (toggle.matches(line) && !toggle.enabled()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetContinuation(String line) {
        return line.startsWith(" ") || line.startsWith("\t") || line.startsWith("-") || line.startsWith("Tags:");
    }

    private record LineToggle(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value, String prefix) {
        boolean matches(String line) {
            return line.startsWith(prefix);
        }

        boolean enabled() {
            return value.get();
        }
    }

    private enum TargetSection {
        BLOCK(DebugOverlayConfig.SHOW_TARGETED_BLOCK, "Targeted Block:"),
        FLUID(DebugOverlayConfig.SHOW_TARGETED_FLUID, "Targeted Fluid:"),
        ENTITY(DebugOverlayConfig.SHOW_TARGETED_ENTITY, "Targeted Entity:"),
        NONE(null, "");

        private final net.neoforged.neoforge.common.ModConfigSpec.BooleanValue enabled;
        private final String prefix;

        TargetSection(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue enabled, String prefix) {
            this.enabled = enabled;
            this.prefix = prefix;
        }

        boolean isEnabled() {
            return enabled == null || enabled.get();
        }

        static TargetSection fromLine(String line) {
            if (line == null) {
                return NONE;
            }
            for (TargetSection section : values()) {
                if (section != NONE && line.startsWith(section.prefix)) {
                    return section;
                }
            }
            return NONE;
        }
    }
}
