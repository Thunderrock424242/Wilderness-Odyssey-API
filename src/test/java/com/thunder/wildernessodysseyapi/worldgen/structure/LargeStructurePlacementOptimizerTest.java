package com.thunder.wildernessodysseyapi.worldgen.structure;

import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the per-axis structure size guard used by the custom template loader. */
class LargeStructurePlacementOptimizerTest {

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
}
