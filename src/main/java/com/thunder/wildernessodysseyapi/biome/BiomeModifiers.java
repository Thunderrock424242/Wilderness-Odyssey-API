package com.thunder.wildernessodysseyapi.biome;


import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public class BiomeModifiers implements BiomeModifier {
    private final Holder<ConfiguredStructureFeature<?, ?>> structure;

    public BiomeModifiers(Holder<ConfiguredStructureFeature<?, ?>> structure) {
        this.structure = structure;
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && biome.unwrapKey().map(this::isApplicableBiome).orElse(false)) {
            builder.getGenerationSettings().addFeature(
                    GenerationStep.Decoration.SURFACE_STRUCTURES,
                    structure
            );
        }
    }

    private boolean isApplicableBiome(ResourceKey<Biome> biomeKey) {
        // Replace this with your logic to select specific biomes
        return biomeKey.equals(Biomes.PLAINS) || biomeKey.equals(Biomes.DESERT);
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        // Return a codec for serialization (required for modern Forge)
        return (MapCodec<? extends BiomeModifier>) ModBiomeModifiers.CODEC;
    }
}
