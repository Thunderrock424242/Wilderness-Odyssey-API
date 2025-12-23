package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.util.NbtCompressionUtils;
import com.thunder.wildernessodysseyapi.io.CompressionCodec;
import com.thunder.wildernessodysseyapi.util.NbtDataCompactor;
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
    private final CompressionCodec compressionCodec;

    public DiskChunkStorageAdapter(Path root, int compressionLevel, CompressionCodec compressionCodec) {
        this.root = root;
        this.compressionLevel = compressionLevel;
        this.compressionCodec = compressionCodec;
    }

    @Override
    public Optional<CompoundTag> read(ChunkPos pos) throws IOException {
        Path path = chunkPath(pos);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        CompoundTag payload = NbtCompressionUtils.readCompressed(path, compressionCodec);
        NbtDataCompactor.expandModPayload(payload);
        return Optional.of(payload);
    }

    @Override
    public void write(ChunkPos pos, CompoundTag tag) throws IOException {
        Path path = chunkPath(pos);
        CompoundTag compacted = tag.copy();
        NbtDataCompactor.compactModPayload(compacted);
        NbtCompressionUtils.writeCompressed(path, compacted, compressionLevel, compressionCodec);
    }

    private Path chunkPath(ChunkPos pos) {
        String fileName = pos.x + "_" + pos.z + ".nbt";
        return root.resolve(fileName);
    }
}
