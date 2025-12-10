package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * Result returned when a chunk load finishes.
 */
public record ChunkLoadResult(ChunkPos pos, CompoundTag payload, boolean fromCache) {
}
