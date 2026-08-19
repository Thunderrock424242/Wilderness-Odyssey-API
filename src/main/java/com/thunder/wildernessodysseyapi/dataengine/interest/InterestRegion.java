package com.thunder.wildernessodysseyapi.dataengine.interest;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** IMMUTABLE chunk/cell location used by spatial interest queries. */
public record InterestRegion(ResourceLocation dimension, int chunkX, int chunkZ) {
    public InterestRegion {
        dimension = Objects.requireNonNull(dimension, "Region dimension is required");
    }
}
