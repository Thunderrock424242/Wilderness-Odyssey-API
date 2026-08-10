package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;

import java.util.ArrayList;
import java.util.List;

/** Repackages the exact vanilla/NeoForge debug line collections as an escape-hatch page. */
public final class VanillaDebugDataProvider implements DebugDataProvider {
    @Override
    public List<DebugSection> collect(DebugContext context) {
        List<DebugSection> sections = new ArrayList<>(2);
        sections.add(rawSection("VANILLA LEFT / GAME", context.vanillaLeft()));
        sections.add(rawSection("VANILLA RIGHT / SYSTEM & TARGET", context.vanillaRight()));
        return List.copyOf(sections);
    }

    private static DebugSection rawSection(String title, List<String> lines) {
        DebugSection.Builder section = DebugSection.builder(title);
        if (lines.isEmpty()) {
            return section.addRaw("No vanilla lines were produced.").build();
        }
        for (String line : lines) {
            section.addRaw(line == null || line.isEmpty() ? " " : line);
        }
        return section.build();
    }
}
