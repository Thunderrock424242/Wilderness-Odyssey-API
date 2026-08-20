package com.thunder.wildernessodysseyapi.structureblock;

import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies overflow-safe structure volume and loaded-chunk budget calculations. */
class StructureBlockWorkBudgetTest {

    @Test
    void calculatesExpandedStructureVolumeUsingLongArithmetic() {
        assertEquals(134_217_728L, StructureBlockWorkBudget.volume(new Vec3i(512, 512, 512)));
    }

    @Test
    void saturatesMalformedDimensionsInsteadOfOverflowingBelowTheBudget() {
        assertEquals(Long.MAX_VALUE,
                StructureBlockWorkBudget.volume(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void treatsIncompleteDimensionsAsNoVolume() {
        assertEquals(0L, StructureBlockWorkBudget.volume(512, 0, 512));
        assertEquals(0L, StructureBlockWorkBudget.volume(512, -1, 512));
    }

    @Test
    void distinguishesReasonableWorkFromLegacyAxisMaximum() {
        long operationBudget = 4_194_304L;

        assertTrue(StructureBlockWorkBudget.volume(128, 128, 128) <= operationBudget);
        assertTrue(StructureBlockWorkBudget.volume(512, 512, 512) > operationBudget);
    }

    @Test
    void countsChunksAcrossNegativeCoordinateBoundary() {
        assertEquals(4L, StructureBlockWorkBudget.chunkCount(-1, 0, -1, 0));
    }

    @Test
    void normalizesReversedBlockBounds() {
        assertEquals(9L, StructureBlockWorkBudget.chunkCount(32, 0, 32, 0));
    }
}
