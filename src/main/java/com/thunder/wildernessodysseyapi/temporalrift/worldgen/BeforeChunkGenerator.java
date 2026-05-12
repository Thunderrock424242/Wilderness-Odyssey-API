package com.thunder.wildernessodysseyapi.temporalrift.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thunder.wildernessodysseyapi.temporalrift.BeforeStructurePolicy;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftWorldgen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.stream.Stream;

public class BeforeChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<BeforeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(BeforeChunkGenerator::generatorSettings)
    ).apply(instance, BeforeChunkGenerator::new));

    public BeforeChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        HolderLookup.RegistryLookup<StructureSet> lookup = (HolderLookup.RegistryLookup<StructureSet>) structureSets;
        Stream<Holder<StructureSet>> allowed = lookup.listElements()
                .filter(holder -> holder.unwrapKey().map(BeforeStructurePolicy::isAllowed).orElse(false))
                .map(holder -> (Holder<StructureSet>) holder);
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, allowed);
    }
}
