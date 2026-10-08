package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.vegetation.VegetationChunkFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VegetationSamplingTest {
    @Test
    void surfaceProbeTargetsTheFlowerInsteadOfItsSupportingDirt() {
        var chunk = VegetationChunkFixture.create();
        chunk.setBlockState(new BlockPos(8, 64, 8), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 65, 8), Blocks.DANDELION.defaultBlockState(), false);

        assertEquals(new BlockPos(8, 65, 8), ReactiveVegetationScheduler.selectedPosition(chunk, 8, 8, 0));
    }

    @Test
    void alternatingProbeFindsTheGroundPlantBelowATallLeafCanopy() {
        var chunk = VegetationChunkFixture.create();
        chunk.setBlockState(new BlockPos(8, 64, 8), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 65, 8), Blocks.DANDELION.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 90, 8), Blocks.OAK_LEAVES.defaultBlockState(), false);

        assertEquals(new BlockPos(8, 90, 8), ReactiveVegetationScheduler.selectedPosition(chunk, 8, 8, 0));
        assertEquals(new BlockPos(8, 65, 8), ReactiveVegetationScheduler.selectedPosition(chunk, 8, 8, 1));
    }
}
