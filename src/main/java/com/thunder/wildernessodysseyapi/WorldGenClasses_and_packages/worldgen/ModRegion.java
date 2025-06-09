package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.worldgen;

import net.minecraft.resources.ResourceLocation;
import terrablender.api.Region;
import terrablender.api.RegionType;
import net.minecraft.world.level.biome.Climate;

public class ModRegion extends Region {
    public ModRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }


    public void addBiomes(net.minecraft.data.worldgen.BootstrapContext<net.minecraft.world.level.biome.Biome> context) {
        // Use one very specific climate point so it only ever spawns once:
        Climate.ParameterPoint parameterPoint = new Climate.ParameterPoint(
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F),
                0L
        );
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> anomalyZoneKey =
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        ModBiomes.ANOMALY_ZONE.location()
                );
    }
}
