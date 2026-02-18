package com.thunder.wildernessodysseyapi.WorldGen.biome;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBiomes {
    private ModBiomes() {
    }

    public static final DeferredRegister<Biome> BIOMES = DeferredRegister.create(Registries.BIOME, ModConstants.MOD_ID);

    public static final ResourceKey<Biome> ANOMALY_PLAINS_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_plains"));
    public static final ResourceKey<Biome> ANOMALY_DESERT_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_desert"));
    public static final ResourceKey<Biome> ANOMALY_TUNDRA_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_tundra"));
    public static final ResourceKey<Biome> ANOMALY_RAINFOREST_KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_rainforest"));

    public static final DeferredHolder<Biome, Biome> ANOMALY_PLAINS = BIOMES.register("anomaly_plains", AnomalyBiomes::anomalyPlains);
    public static final DeferredHolder<Biome, Biome> ANOMALY_DESERT = BIOMES.register("anomaly_desert", AnomalyBiomes::anomalyDesert);
    public static final DeferredHolder<Biome, Biome> ANOMALY_TUNDRA = BIOMES.register("anomaly_tundra", AnomalyBiomes::anomalyTundra);
    public static final DeferredHolder<Biome, Biome> ANOMALY_RAINFOREST = BIOMES.register("anomaly_rainforest", AnomalyBiomes::anomalyRainforest);
}
