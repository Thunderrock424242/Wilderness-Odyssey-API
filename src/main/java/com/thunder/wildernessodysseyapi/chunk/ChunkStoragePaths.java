package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Resolves per-world or global chunk cache paths.
 */
public final class ChunkStoragePaths {
    private ChunkStoragePaths() {
    }

    public static Path resolveCacheRoot(MinecraftServer server, ChunkStreamingConfig.ChunkConfigValues config) {
        String folderName = config.cacheFolderName();
        if (folderName == null || folderName.isBlank()) {
            folderName = "chunk-cache";
        }
        if (config.storeCacheInWorldConfig()) {
            return server.getWorldPath(LevelResource.SERVERCONFIG).resolve(folderName);
        }
        return server.getFile("config/" + com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID + "/" + folderName).toPath();
    }
}
