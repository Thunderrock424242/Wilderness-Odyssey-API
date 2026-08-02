package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the exact fixed-point invariant independently of Minecraft world state. */
class CanonicalWaterBucketTransactionsTest {

    @Test
    void onlyOneCompleteBlockFundsABucket() {
        assertTrue(CanonicalWaterBucketTransactions.isExactBucketVolume(
                WaterVolumeChunk.UNITS_PER_BLOCK
        ));
        assertFalse(CanonicalWaterBucketTransactions.isExactBucketVolume(
                WaterVolumeChunk.UNITS_PER_BLOCK - 1
        ));
        assertFalse(CanonicalWaterBucketTransactions.isExactBucketVolume(0));
        assertFalse(CanonicalWaterBucketTransactions.isExactBucketVolume(
                WaterVolumeChunk.UNITS_PER_BLOCK + 1
        ));
    }
}
