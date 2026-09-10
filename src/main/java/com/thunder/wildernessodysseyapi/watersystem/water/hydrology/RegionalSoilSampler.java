package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Sixteen loaded-column probes cache soil class and vegetation only on admission/terrain edits. */
final class RegionalSoilSampler {
    private RegionalSoilSampler() { }

    static void sample(LevelChunk chunk, RegionalHydrologyState region) {
        int[] counts = new int[SoilHydrologyModel.SoilProfile.values().length];
        int vegetation = 0;
        for (int z = 2; z < 16; z += 4) for (int x = 2; x < 16; x += 4) {
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockState block = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x,
                    y, chunk.getPos().getMinBlockZ() + z));
            counts[classify(block).ordinal()]++;
            if (block.is(Blocks.GRASS_BLOCK) || block.is(BlockTags.LEAVES)) vegetation++;
        }
        int winner = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[winner]) winner = i;
        region.soil = SoilHydrologyModel.SoilProfile.values()[winner];
        region.vegetation = vegetation / 16.0;
    }

    private static SoilHydrologyModel.SoilProfile classify(BlockState block) {
        if (block.is(WatershedTags.SOIL_IMPERMEABLE)) return SoilHydrologyModel.SoilProfile.IMPERMEABLE;
        if (block.is(WatershedTags.SOIL_GRAVEL)) return SoilHydrologyModel.SoilProfile.GRAVEL;
        if (block.is(WatershedTags.SOIL_SAND)) return SoilHydrologyModel.SoilProfile.SAND;
        if (block.is(WatershedTags.SOIL_CLAY)) return SoilHydrologyModel.SoilProfile.CLAY;
        if (block.is(WatershedTags.SOIL_ROCK)) return SoilHydrologyModel.SoilProfile.ROCK;
        return SoilHydrologyModel.SoilProfile.LOAM;
    }
}
