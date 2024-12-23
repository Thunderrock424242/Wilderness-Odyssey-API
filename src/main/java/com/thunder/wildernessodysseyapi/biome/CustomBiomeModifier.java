package com.thunder.wildernessodysseyapi.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public class CustomBiomeModifier implements BiomeModifier {
    private final ResourceKey<PlacedFeature> structure;
    private final ResourceKey<Biome> targetBiome;
    private final int minBiomeSize;

    // Define the Codec for serialization/deserialization
    public static final Codec<CustomBiomeModifier> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceKey.codec(ForgeRegistries.Keys.PLACED_FEATURES).fieldOf("structure").forGetter(modifier -> modifier.structure),
                    ResourceKey.codec(ForgeRegistries.Keys.BIOMES).fieldOf("target_biome").forGetter(modifier -> modifier.targetBiome),
                    Codec.INT.fieldOf("min_biome_size").forGetter(modifier -> modifier.minBiomeSize)
            ).apply(instance, CustomBiomeModifier::new)
    );

    public CustomBiomeModifier(ResourceKey<PlacedFeature> structure, ResourceKey<Biome> targetBiome, int minBiomeSize) {
        this.structure = structure;
        this.targetBiome = targetBiome;
        this.minBiomeSize = minBiomeSize;
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && biome.unwrapKey().orElseThrow().equals(targetBiome)) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, (Holder<PlacedFeature>) structure);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return (MapCodec<? extends BiomeModifier>) CODEC;
    }
}
