package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.fml.ModList;
import terrablender.api.Regions;

public final class BiomeCompatibilityBootstrap {
    private static boolean initialized;

    private BiomeCompatibilityBootstrap() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ModList modList = ModList.get();

        if (modList.isLoaded("terrablender")) {
            Regions.register(new AnomalyTerraBlenderRegion());
            ModConstants.LOGGER.info("Registered anomaly biome TerraBlender region for overworld/large-biome compatibility.");
        }

        if (modList.isLoaded("biolith")) {
            ModConstants.LOGGER.info("Biolith detected; anomaly biomes exposed through overworld/common biome tags.");
        }

        if (modList.isLoaded("lithostitched")) {
            ModConstants.LOGGER.info("Lithostitched detected; anomaly biomes exposed through overworld/common biome tags.");
        }
    }
}
