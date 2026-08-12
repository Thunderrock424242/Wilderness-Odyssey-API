package com.thunder.wildernessodysseyapi.developmentstudio;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Resolves and initializes the persistent Development Studio world identity. */
public final class StudioWorldAccess {
    private StudioWorldAccess() {
    }

    /** Initializes saved identity from the vanilla-derived noise-settings marker. */
    public static boolean initializeFromPreset(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)
                || !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
                || !generator.generatorSettings().is(StudioWorldKeys.DEVELOPMENT_STUDIO_NOISE_SETTINGS)) {
            return false;
        }
        StudioWorldData data = StudioWorldData.getOrCreate(level.getServer());
        data.markDevelopmentStudioWorld();
        return true;
    }

    /** Returns the persistent identity without relying on the current dimension key. */
    public static boolean isDevelopmentStudioWorld(MinecraftServer server) {
        return StudioWorldData.find(server)
                .map(StudioWorldData::isDevelopmentStudioWorld)
                .orElse(false);
    }
}
