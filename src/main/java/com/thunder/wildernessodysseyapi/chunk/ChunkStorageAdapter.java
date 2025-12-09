package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.util.Optional;

/**
 * Abstraction for reading and writing chunk NBT payloads.
 */
public interface ChunkStorageAdapter {
    Optional<CompoundTag> read(ChunkPos pos) throws IOException;

    void write(ChunkPos pos, CompoundTag tag) throws IOException;
}
