package com.thunder.wildernessodysseyapi.developmentstudio.structure;

import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.resources.ResourceLocation;

/** One allowlisted structure template exposed to Development Studio previews. */
public record StudioStructureDefinition(
        ResourceLocation id,
        String displayName,
        boolean labPlaceable,
        NBTStructurePlacer placer
) {
    public StudioStructureDefinition {
        if (id == null || displayName == null || placer == null) {
            throw new IllegalArgumentException("Studio structure definition fields cannot be null");
        }
    }
}
