package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.world.level.biome.Biome;

public class BiomeUtils {

    public static boolean isCaveBiome(Biome biome) {
        // Replace with your criteria for identifying cave biomes
        return biome.getBiomeCategory() == Biome.BiomeCategory.UNDERGROUND;
    }
}