package com.thunder.wildernessodysseyapi.vegetation;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Actual block sections and final heightmaps without a loaded-server lifecycle. */
public final class VegetationChunkFixture {
    private VegetationChunkFixture() {
    }

    public static ProtoChunk create() {
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Registry.register(biomes, Biomes.PLAINS, new Biome.BiomeBuilder()
                .hasPrecipitation(false).temperature(0.8F).downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0).waterFogColor(0)
                        .fogColor(0).skyColor(0).build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(BiomeGenerationSettings.EMPTY).build());
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384), biomes, null);
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        return chunk;
    }
}
