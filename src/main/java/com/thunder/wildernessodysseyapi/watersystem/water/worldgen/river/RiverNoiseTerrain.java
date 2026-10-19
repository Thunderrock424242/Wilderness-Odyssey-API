package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Supported generator-only prediction: the height accessor exposes no chunk lookup API. */
public final class RiverNoiseTerrain {
    private RiverNoiseTerrain() { }

    public static RiverConnectedPlanner.Terrain predict(NoiseBasedChunkGenerator generator,
            LevelHeightAccessor heights, RandomState random, int x, int z) {
        if (generator.getClass() != NoiseBasedChunkGenerator.class) throw new IllegalArgumentException("Unsupported terrain predictor");
        var column = generator.getBaseColumn(x, z, heights, random);
        int y = heights.getMaxBuildHeight() - 1;
        while (y > heights.getMinBuildHeight() && column.getBlock(y).isAir()) y--;
        var top = column.getBlock(y);
        var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                QuartPos.fromBlock(z), random.sampler()).value();
        float rain = Math.max(0, Math.min(1, biome.getModifiedClimateSettings().downfall()));
        return new RiverConnectedPlanner.Terrain(y, top.is(Blocks.WATER) && top.getFluidState().isSource(), rain);
    }
}
