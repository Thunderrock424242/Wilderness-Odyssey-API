package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies exact temporary ownership, persistence, and conservative recession gates. */
class TemporaryFloodSavedDataTest {

    @Test
    void ledgerRoundTripRetainsExactChunkCounts() {
        TemporaryFloodSavedData data = new TemporaryFloodSavedData();
        BlockPos first = new BlockPos(18, 64, -3);
        BlockPos second = new BlockPos(19, 64, -3);
        data.record(first, 44L, 100L, 16);
        data.record(second, 44L, 101L, 16);

        CompoundTag encoded = data.save(new CompoundTag(), null);
        TemporaryFloodSavedData decoded = TemporaryFloodSavedData.load(encoded, null);

        assertEquals(2, decoded.size());
        assertEquals(2, decoded.countInChunk(ChunkPos.asLong(1, -1)));
    }

    @Test
    void recessionRequiresLedgerFlagAndMatchingProjectionTogether() {
        int floodFlags = WaterVolumeChunk.FLAG_TEMPORARY_FLOOD
                | WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED;

        assertTrue(TemporaryFloodSavedData.mayRemoveTrackedCell(true, floodFlags, true));
        assertFalse(TemporaryFloodSavedData.mayRemoveTrackedCell(false, floodFlags, true));
        assertFalse(TemporaryFloodSavedData.mayRemoveTrackedCell(
                true,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                true
        ));
        assertFalse(TemporaryFloodSavedData.mayRemoveTrackedCell(true, floodFlags, false));
    }
}
