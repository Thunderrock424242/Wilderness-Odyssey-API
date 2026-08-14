package com.thunder.wildernessodysseyapi.developmentstudio.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** One fixed entity type that authorized Studio operators may spawn in the Entity Lab. */
public record StudioEntityDefinition(
        ResourceLocation id,
        String displayName,
        EntityType<?> entityType
) {
}
