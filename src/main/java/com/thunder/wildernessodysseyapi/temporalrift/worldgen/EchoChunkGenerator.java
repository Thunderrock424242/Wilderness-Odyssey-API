package com.thunder.wildernessodysseyapi.temporalrift.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import com.thunder.wildernessodysseyapi.temporalrift.echo.structure.EchoStructurePolicy;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** Same-seed Overworld geography with an opt-in, independent human structure history. */
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

    /** Keeps Minecraft's normal placement and ring seeds; only explicitly authored sets diverge. */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForNormal(random, seed, biomeSource,
                EchoStructurePolicy.forDimension(structures, true));
    }
}
