package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Defines one named location relative to the persisted campus template origin.
 *
 * @param id stable internal location id
 * @param displayName player-facing location label
 * @param offset template-relative feet position
 * @param available whether Phase 1 contains a usable destination at this offset
 */
public record StudioLocationDefinition(
        ResourceLocation id,
        String displayName,
        BlockPos offset,
        boolean available
) {
    public StudioLocationDefinition {
        if (id == null || displayName == null || displayName.isBlank() || offset == null) {
            throw new IllegalArgumentException("Studio location fields must be present");
        }
        offset = offset.immutable();
    }
}
