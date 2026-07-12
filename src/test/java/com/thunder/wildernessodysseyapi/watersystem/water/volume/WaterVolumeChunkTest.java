package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact canonical water persistence without requiring a loaded world. */
class WaterVolumeChunkTest {

    @Test
    void roundTripsVolumeVelocityFlagsAndTemperature() {
        BlockPos pos = new BlockPos(31, -42, -17);
        WaterVolumeChunk original = new WaterVolumeChunk();
        original.set(pos, new WaterVolumeChunk.WaterCell(
                3_072,
                0.25f,
                -0.5f,
                1.25f,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED | WaterVolumeChunk.FLAG_HOSTED_WATER,
                288_150
        ));

        CompoundTag encoded = original.serializeNBT(null);
        WaterVolumeChunk decoded = new WaterVolumeChunk();
        decoded.deserializeNBT(null, encoded);

        assertTrue(decoded.contains(pos));
        assertEquals(original.get(pos), decoded.get(pos));
        assertTrue(decoded.get(pos).hostedWater());
        assertEquals(original.revision(), decoded.revision());
    }

    @Test
    void zeroVolumeRemovesSparseCell() {
        BlockPos pos = new BlockPos(3, 70, 12);
        WaterVolumeChunk volume = new WaterVolumeChunk();
        volume.set(pos, WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                0
        ));
        volume.set(pos, WaterVolumeChunk.WaterCell.EMPTY);

        assertFalse(volume.contains(pos));
        assertEquals(0, volume.get(pos).volumeUnits());
    }

    @Test
    void dryGeneratedOverrideSurvivesPersistence() {
        BlockPos pos = new BlockPos(15, 62, 0);
        WaterVolumeChunk original = new WaterVolumeChunk();
        WaterVolumeChunk.WaterCell dryOverride = WaterVolumeChunk.WaterCell.still(
                0,
                WaterVolumeChunk.FLAG_GENERATED_OVERRIDE | WaterVolumeChunk.FLAG_DRY_OVERRIDE
        );
        original.set(pos, dryOverride);

        WaterVolumeChunk decoded = new WaterVolumeChunk();
        decoded.deserializeNBT(null, original.serializeNBT(null));

        assertTrue(decoded.contains(pos));
        assertEquals(dryOverride, decoded.get(pos));
    }

    @Test
    void packedPositionPreservesNegativeWorldHeight() {
        BlockPos source = new BlockPos(-33, -64, 48);
        int packed = WaterVolumeChunk.pack(source);
        BlockPos decoded = WaterVolumeChunk.unpack(-3, 3, packed);

        assertEquals(source, decoded);
    }

    @Test
    void persistenceDoesNotTruncateLargeSparseChunks() {
        int cellCount = 16_385;
        WaterVolumeChunk original = new WaterVolumeChunk();
        for (int index = 0; index < cellCount; index++) {
            BlockPos pos = new BlockPos(index & 15, index >>> 8, (index >>> 4) & 15);
            original.set(pos, WaterVolumeChunk.WaterCell.still(1_024, 0));
        }

        WaterVolumeChunk decoded = new WaterVolumeChunk();
        decoded.deserializeNBT(null, original.serializeNBT(null));

        assertEquals(cellCount, decoded.snapshot().size());
        assertEquals(1_024, decoded.get(new BlockPos(0, 64, 0)).volumeUnits());
    }
}
