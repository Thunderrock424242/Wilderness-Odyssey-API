package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class ModBiomes {
    private ModBiomes() {
    }

    @Deprecated
    public static final ResourceKey<Biome> ANOMALY_PLAINS_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_plains"));
    public static final ResourceKey<Biome> ANOMALY_TUNDRA_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_tundra"));
    public static final ResourceKey<Biome> ANOMALY_RAINFOREST_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_rainforest"));
    /**
     * Backward-compatible alias used by commands/datapacks that still reference
     * {@code wildernessodysseyapi:anomaly_zone}.
     */
    @Deprecated
    public static final ResourceKey<Biome> ANOMALY_ZONE_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_zone"));
    @Deprecated
    public static final ResourceKey<Biome> ANOMALY_DESERT_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_desert"));
}
