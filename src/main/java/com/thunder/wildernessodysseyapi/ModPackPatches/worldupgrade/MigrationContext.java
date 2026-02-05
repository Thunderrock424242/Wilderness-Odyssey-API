package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

import com.thunder.wildernessodysseyapi.capabilities.ChunkDataCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Runtime context for a chunk migration task.
 */
public record MigrationContext(ServerLevel level, LevelChunk chunk, ChunkDataCapability chunkData) {
}
