package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.worldgen;

import net.minecraft.resources.ResourceLocation;
import terrablender.api.Region;
import terrablender.api.RegionType;
import net.minecraft.world.level.biome.Climate;

public class ModRegion extends Region {
    public ModRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(BiomeManager.BiomeRegionBuilder builder) {
        // Use one very specific climate point so it only ever spawns once:
        builder.addBiome(
                Climate.ParameterPoint.of(
                        Climate.Parameter.point(0.01123F), // a unique "temperature" value
                        Climate.Parameter.point(0.01123F), // a unique "humidity" value
                        Climate.Parameter.point(0.0F),     // erosion
                        Climate.Parameter.point(0.0F),     // depth
                        Climate.Parameter.point(0.0F),     // weirdness
                        0L
                ),
                ModBiomes.ANOMALY_ZONE
        );
    }
}
