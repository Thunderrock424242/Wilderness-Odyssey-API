package com.thunder.wildernessodysseyapi.worldgen;

import net.minecraft.data.worldgen.biome.NetherBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import javax.swing.plaf.synth.Region;
import java.util.Optional;
import java.util.function.Consumer;

public class ModRegion extends NetherBiomes {
    public ModRegion(ResourceLocation name, int weight) {
        super();
    }

    public void addBiomes(Region region, Consumer<Climate.ParameterPoint> adder) {
        Climate.ParameterPoint point = new Climate.ParameterPoint(
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.01123F),
                Climate.Parameter.point(0.01123F),
                0L
        );
        adder.accept(point);

    }


    public Optional<ResourceKey<Biome>> getBiome(Climate.TargetPoint target) {
        // Return your biome if it matches the conditions
        return Optional.of(ModBiomes.ANOMALY_REGION);
    }

}