package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies deterministic cross-region basin aliases and persistence. */
class WatershedBasinSavedDataTest {

    @Test
    void unionUsesUnsignedMinimumAndSurvivesRoundTrip() {
        WatershedBasinSavedData data = new WatershedBasinSavedData();
        long highUnsigned = -2L;
        long lowUnsigned = 17L;

        assertEquals(lowUnsigned, data.union(highUnsigned, lowUnsigned));
        assertEquals(lowUnsigned, data.resolve(highUnsigned));

        CompoundTag encoded = data.save(new CompoundTag(), null);
        WatershedBasinSavedData decoded = WatershedBasinSavedData.load(encoded, null);
        assertEquals(lowUnsigned, decoded.resolve(highUnsigned));
        assertEquals(lowUnsigned, decoded.resolve(lowUnsigned));
    }

    @Test
    void chainedAliasesCompressToOneCanonicalBasin() {
        WatershedBasinSavedData data = new WatershedBasinSavedData();
        data.union(900L, 700L);
        data.union(700L, 500L);

        assertEquals(500L, data.resolve(900L));
        assertEquals(500L, data.resolve(700L));
    }
}
