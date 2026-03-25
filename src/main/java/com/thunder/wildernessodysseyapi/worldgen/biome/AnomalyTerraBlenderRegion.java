package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Climate;

public final class AnomalyTerraBlenderRegion extends Region {
    private static final int REGION_WEIGHT = 3;

    public AnomalyTerraBlenderRegion() {
        super(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_overworld"), RegionType.OVERWORLD, REGION_WEIGHT);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        addModifiedVanillaOverworldBiomes(mapper, builder -> {
            builder.replaceBiome(Biomes.PLAINS, ModBiomes.ANOMALY_PLAINS_KEY);
            builder.replaceBiome(Biomes.FOREST, ModBiomes.ANOMALY_PLAINS_KEY);
            builder.replaceBiome(Biomes.JUNGLE, ModBiomes.ANOMALY_RAINFOREST_KEY);
            builder.replaceBiome(Biomes.SNOWY_PLAINS, ModBiomes.ANOMALY_TUNDRA_KEY);
        });
    }
}
