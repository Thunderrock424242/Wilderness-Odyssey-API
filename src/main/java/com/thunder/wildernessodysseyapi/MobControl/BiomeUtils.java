package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class BiomeUtils {

    public static boolean isCaveBiome(Holder<Biome> biomeHolder) {
        // Extract the ResourceLocation from the biome holder
        ResourceLocation biomeKey = biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);

        if (biomeKey != null) {
            // Check if the biome name contains keywords like "cave" or "underground"
            String biomeName = biomeKey.toString().toLowerCase();
            return biomeName.contains("cave") || biomeName.contains("underground");
        }

        return false; // Default to false if no valid biomeKey is found
    }
}
