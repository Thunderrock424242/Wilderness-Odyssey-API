package com.thunder.wildernessodysseyapi.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public class ModTags {
    public static class Biomes {
        // This tag allows ANY biome (even from other mods) to have Purple Storms if added via datapack
        public static final TagKey<Biome> IS_ANOMALY_ZONE = tag("is_anomaly_zone");

        private static TagKey<Biome> tag(String name) {
            return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, name));
        }
    }
}