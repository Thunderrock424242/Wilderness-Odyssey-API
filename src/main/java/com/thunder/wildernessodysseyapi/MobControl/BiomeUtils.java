package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class BiomeUtils {

    public static boolean isCaveBiome(Biome biome) {
        // Attempt to get the registry name of the biome
        ResourceLocation biomeKey = biome.getRegistryName(); // Adjusted for direct Biome access

        if (biomeKey != null) {
            String biomeName = biomeKey.toString().toLowerCase();
            return biomeName.contains("cave") || biomeName.contains("underground");
        }

        return false; // Default to false if we can't identify the biome
    }
}
