package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.jetbrains.annotations.NotNull;

/**
 * The type Custom biome modifier.
 */
public class CustomBiomeModifier implements BiomeModifier {
    private final ResourceKey<PlacedFeature> structure;
    private final ResourceKey<Biome> targetBiome;
    private final int minBiomeSize;

    /**
     * The constant CODEC.
     */
// Define the Codec for serialization/deserialization
    public static final Codec<CustomBiomeModifier> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceKey.codec(Registries.PLACED_FEATURE).fieldOf("structure").forGetter(modifier -> modifier.structure),
                    ResourceKey.codec(Registries.BIOME).fieldOf("target_biome").forGetter(modifier -> modifier.targetBiome),
                    Codec.INT.fieldOf("min_biome_size").forGetter(modifier -> modifier.minBiomeSize)
            ).apply(instance, CustomBiomeModifier::new)
    );

    /**
     * Instantiates a new Custom biome modifier.
     *
     * @param structure    the structure
     * @param targetBiome  the target biome
     * @param minBiomeSize the min biome size
     */
    public CustomBiomeModifier(ResourceKey<PlacedFeature> structure, ResourceKey<Biome> targetBiome, int minBiomeSize) {
        this.structure = structure;
        this.targetBiome = targetBiome;
        this.minBiomeSize = minBiomeSize;
    }

    @Override
    public void modify(@NotNull Holder<Biome> biome, @NotNull Phase phase, ModifiableBiomeInfo.BiomeInfo.@NotNull Builder builder) {
        if (phase == Phase.ADD && biome.unwrapKey().orElseThrow().equals(targetBiome)) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, (Holder<PlacedFeature>) structure);
        }
    }

    @Override
    public @NotNull MapCodec<? extends BiomeModifier> codec() {
        return (MapCodec<? extends BiomeModifier>) CODEC;
    }
}
