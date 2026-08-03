package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies fractional persistence and signed transfer consumption. */
class HydrologySavedDataTest {

    @Test
    void fractionalBalancesRoundTripAndConsumeCommittedUnits() {
        HydrologySavedData data = new HydrologySavedData();
        long chunkKey = 42L;
        BlockPos representative = new BlockPos(10, 63, -4);

        data.accumulate(chunkKey, representative, 64.75, 120L);
        CompoundTag tag = data.save(new CompoundTag(), null);
        HydrologySavedData decoded = HydrologySavedData.load(tag, null);

        assertEquals(64.75, decoded.balanceUnits(chunkKey), 1.0e-9);
        assertEquals(1, decoded.entryCount());

        decoded.consume(chunkKey, 64L);
        assertEquals(0.75, decoded.balanceUnits(chunkKey), 1.0e-9);
        decoded.accumulate(chunkKey, representative, -1.0, 140L);
        assertEquals(-0.25, decoded.balanceUnits(chunkKey), 1.0e-9);
    }
}
