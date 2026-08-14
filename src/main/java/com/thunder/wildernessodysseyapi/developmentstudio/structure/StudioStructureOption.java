package com.thunder.wildernessodysseyapi.developmentstudio.structure;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

/** Bounded structure-catalog row sent to an authorized Studio client. */
public record StudioStructureOption(
        ResourceLocation id,
        String displayName,
        Vec3i size,
        boolean labPlaceable
) {
    public StudioStructureOption {
        if (id == null || size == null) {
            throw new IllegalArgumentException("Studio structure option fields cannot be null");
        }
        displayName = StudioText.singleLine(displayName, 64);
    }
}
