package com.thunder.wildernessodysseyapi.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public class ModTags {
    public static class Biomes {
        public static final TagKey<Biome> IS_ANOMALY_FOREST = tag("is_anomaly_forest");
        public static final TagKey<Biome> IS_GLACIAL = tag("is_glacial");
        public static final TagKey<Biome> IS_GLACIAL_INLAND = tag("is_glacial_inland");
        public static final TagKey<Biome> HAS_GLACIAL_RIVERS = tag("has_glacial_rivers");
        public static final TagKey<Biome> HAS_GLACIAL_WATERFALLS = tag("has_glacial_waterfalls");
        public static final TagKey<Biome> IS_COASTAL = tag("is_coastal");

        private static TagKey<Biome> tag(String name) {
            return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, name));
        }
    }
}
