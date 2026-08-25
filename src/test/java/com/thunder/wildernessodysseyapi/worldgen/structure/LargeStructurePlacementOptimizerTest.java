package com.thunder.wildernessodysseyapi.worldgen.structure;

import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the per-axis structure size guard used by the custom template loader. */
class LargeStructurePlacementOptimizerTest {
    private static final int STARTER_BUNKER_BLOCKS = 2_171_624;
    private static final int STARTER_BUNKER_ENTITIES = 68;
    private static final long STARTER_BUNKER_ACCOUNTED_NBT_BYTES = 541_918_092L;

    @Test
    void acceptsLargeVolumeWhenEveryAxisIsWithinTheSupportedSpan() {
        assertFalse(LargeStructurePlacementOptimizer.exceedsStructureBlockLimit(
                new Vec3i(104, 133, 157)));
    }

    @Test
    void rejectsAnyAxisBeyondTheSupportedSpan() {
        assertTrue(LargeStructurePlacementOptimizer.exceedsStructureBlockLimit(
                new Vec3i(StructureUtils.STRUCTURE_BLOCK_LIMIT + 1, 1, 1)));
    }

    @Test
    void rejectsTemplatesWhoseVolumeExceedsTheRuntimeBudget() {
        assertFalse(LargeStructurePlacementOptimizer.isWithinTemplateBudget(new Vec3i(256, 256, 256)));
        assertTrue(LargeStructurePlacementOptimizer.isWithinTemplateBudget(new Vec3i(65, 15, 65)));
    }

    @Test
    void rejectsSerializedBlockAndEntityCountsAboveTheRuntimeBudget() {
        assertTrue(LargeStructurePlacementOptimizer.isWithinContentBudget(25_000, 12));
        assertFalse(LargeStructurePlacementOptimizer.isWithinContentBudget(
                LargeStructurePlacementOptimizer.MAX_TEMPLATE_BLOCKS + 1, 0));
        assertFalse(LargeStructurePlacementOptimizer.isWithinContentBudget(
                0, LargeStructurePlacementOptimizer.MAX_TEMPLATE_ENTITIES + 1));
    }

    @Test
    void acceptsTrackedStarterBunkerOnlyWithinItsMeasuredAllowance() {
        assertFalse(LargeStructurePlacementOptimizer.isWithinContentBudget(
                STARTER_BUNKER_BLOCKS, STARTER_BUNKER_ENTITIES));
        assertTrue(LargeStructurePlacementOptimizer.isWithinContentBudget(
                STARTER_BUNKER_BLOCKS,
                STARTER_BUNKER_ENTITIES,
                NBTStructurePlacer.STARTER_BUNKER_MAX_TEMPLATE_BLOCKS));
        assertFalse(LargeStructurePlacementOptimizer.isWithinContentBudget(
                NBTStructurePlacer.STARTER_BUNKER_MAX_TEMPLATE_BLOCKS + 1,
                STARTER_BUNKER_ENTITIES,
                NBTStructurePlacer.STARTER_BUNKER_MAX_TEMPLATE_BLOCKS));
        assertTrue(STARTER_BUNKER_ACCOUNTED_NBT_BYTES
                <= NBTStructurePlacer.STARTER_BUNKER_MAX_DECODED_NBT_BYTES);
    }

    @Test
    void countsTouchedChunksWithoutLoadingThem() {
        assertTrue(LargeStructurePlacementOptimizer.countPlacementChunks(
                new BlockPos(0, 64, 0), new Vec3i(65, 15, 65)) == 25L);
        assertTrue(LargeStructurePlacementOptimizer.countPlacementChunks(
                new BlockPos(15, 64, 15), new Vec3i(2, 1, 2)) == 4L);
    }
}
