package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.util.NbtCompressionUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Simple disk-backed adapter that stores chunk payloads in a configurable directory.
 */
public class DiskChunkStorageAdapter implements ChunkStorageAdapter {
    private final Path root;
    private final int compressionLevel;

    public DiskChunkStorageAdapter(Path root, int compressionLevel) {
        this.root = root;
        this.compressionLevel = compressionLevel;
    }

    @Override
    public Optional<CompoundTag> read(ChunkPos pos) throws IOException {
        Path path = chunkPath(pos);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(NbtCompressionUtils.readCompressed(path));
    }

    @Override
    public void write(ChunkPos pos, CompoundTag tag) throws IOException {
        Path path = chunkPath(pos);
        NbtCompressionUtils.writeCompressed(path, tag, compressionLevel);
    }

    private Path chunkPath(ChunkPos pos) {
        String fileName = pos.x + "_" + pos.z + ".nbt";
        return root.resolve(fileName);
    }
}
