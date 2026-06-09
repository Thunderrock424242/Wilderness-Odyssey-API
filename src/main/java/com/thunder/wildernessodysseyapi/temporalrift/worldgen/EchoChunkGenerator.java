package com.thunder.wildernessodysseyapi.temporalrift.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public class EchoChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<EchoChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(EchoChunkGenerator::generatorSettings)
    ).apply(instance, EchoChunkGenerator::new));

    public EchoChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator> codec() {
        return CODEC;
    }
}
