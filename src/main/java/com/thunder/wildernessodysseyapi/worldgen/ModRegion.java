package com.thunder.wildernessodysseyapi.worldgen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;

public class ModRegion extends NetherRegion {
    public ModRegion(ResourceLocation name, int weight) {
        super(name, weight);
    }

    @Override
    public void addBiomes(BiomeRegionBuilder builder) {
        builder.addBiome(
                Climate.ParameterPoint.of(
                        Climate.Parameter.point(0.01123F),
                        Climate.Parameter.point(0.01123F),
                        Climate.Parameter.point(0.01123F),
                        Climate.Parameter.point(0.01123F),
                        Climate.Parameter.point(0.01123F),
                        0L
                ),
                ModBiomes.METEOR_BIOME
        );
    }
}
