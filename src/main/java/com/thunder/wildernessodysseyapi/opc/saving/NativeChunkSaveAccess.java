package com.thunder.wildernessodysseyapi.opc.saving;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Narrow native save/readiness surface; never controls tickets, scheduling, status, or serialization. */
public interface NativeChunkSaveAccess {
    boolean wildernessodysseyapi$saveChunk(ChunkAccess chunk);
    ChunkHolder wildernessodysseyapi$visibleChunk(long position);
}
