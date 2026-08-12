package com.thunder.wildernessodysseyapi.developmentstudio.inspection;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable server-produced inspection result safe to synchronize to one player. */
public record StudioInspection(
        ResourceLocation providerId,
        String title,
        List<StudioInspectionLine> lines
) {
    public StudioInspection {
        if (providerId == null) {
            throw new IllegalArgumentException("Inspector provider id is required");
        }
        title = StudioText.singleLine(title, 96);
        lines = lines == null ? List.of() : List.copyOf(lines.subList(0, Math.min(lines.size(), 64)));
    }
}
