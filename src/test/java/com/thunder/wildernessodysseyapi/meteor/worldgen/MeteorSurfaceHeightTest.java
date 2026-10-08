package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeteorSurfaceHeightTest {

    @Test
    void laterCraterPassesUseTheSurfaceUpdatedDuringFeatureGeneration() {
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
        chunk.setBlockState(new BlockPos(8, 79, 8), Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 80, 8), Blocks.STONE.defaultBlockState(), false);
        assertEquals(80, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8));
        chunk.setBlockState(new BlockPos(8, 80, 8), Blocks.AIR.defaultBlockState(), false);
        assertEquals(80, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8),
                "The generation-only heightmap intentionally remains stale at CARVERS");

        WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getHeight")) {
                        return chunk.getHeight((Heightmap.Types) arguments[0],
                                (int) arguments[1], (int) arguments[2]) + 1;
                    }
                    throw new AssertionError("Unexpected access: " + method);
                });
        MeteorCraterPlan plan = new MeteorCraterPlan(new BlockPos(8, 80, 8), 60, 12, 8, 20, 1L);
        MeteorPlacementContext placement = new MeteorPlacementContext(level, plan,
                new BoundingBox(0, -64, 0, 15, 319, 15));

        assertEquals(79, placement.surfaceY(8, 8));
    }
}
