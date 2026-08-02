package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void versionedPersistenceStillMigratesTheUnversionedLegacyLayout() {
        BlockPos pos = new BlockPos(7, 45, 9);
        WaterVolumeChunk original = new WaterVolumeChunk();
        original.set(pos, WaterVolumeChunk.WaterCell.still(2_048, WaterVolumeChunk.FLAG_IMPORTED));
        CompoundTag versioned = original.serializeNBT(null);

        CompoundTag legacy = new CompoundTag();
        legacy.putLong("revision", versioned.getLong("revision"));
        legacy.putIntArray("cells", versioned.getIntArray("cells"));
        WaterVolumeChunk migrated = new WaterVolumeChunk();
        migrated.deserializeNBT(null, legacy);

        assertEquals(original.get(pos), migrated.get(pos));
        CompoundTag rewritten = migrated.serializeNBT(null);
        assertEquals(WaterVolumeChunk.FORMAT_VERSION, rewritten.getInt("format_version"));
        assertEquals(1, rewritten.getInt("cell_count"));
    }

    @Test
    void malformedOrFuturePersistenceIsRejectedBeforeReplacingCurrentState() {
        WaterVolumeChunk volume = new WaterVolumeChunk();
        BlockPos retained = new BlockPos(1, 60, 1);
        volume.set(retained, WaterVolumeChunk.WaterCell.still(1_024, 0));

        CompoundTag trailing = new CompoundTag();
        trailing.putIntArray("cells", new int[WaterVolumeChunk.SERIALIZED_CELL_STRIDE + 1]);
        assertThrows(IllegalArgumentException.class, () -> volume.deserializeNBT(null, trailing));
        assertEquals(1_024, volume.get(retained).volumeUnits());

        CompoundTag future = new CompoundTag();
        future.putInt("format_version", WaterVolumeChunk.FORMAT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> volume.deserializeNBT(null, future));
        assertEquals(1_024, volume.get(retained).volumeUnits());
    }

    @Test
    void contiguousDeltasCoalesceUpsertsAndTombstonesExactly() {
        BlockPos first = new BlockPos(2, 63, 3);
        BlockPos second = new BlockPos(4, 64, 5);
        WaterVolumeChunk volume = new WaterVolumeChunk();
        volume.set(first, WaterVolumeChunk.WaterCell.still(1_024, 0));
        long baselineRevision = volume.revision();
        int[] baseline = volume.toNetworkArray();

        volume.set(first, WaterVolumeChunk.WaterCell.still(2_048, 0));
        volume.set(second, WaterVolumeChunk.WaterCell.still(3_072, WaterVolumeChunk.FLAG_SLEEPING));
        volume.set(first, WaterVolumeChunk.WaterCell.EMPTY);

        WaterVolumeChunk.DeltaSnapshot delta = volume.deltaSince(baselineRevision, 16);
        assertTrue(delta.available());
        assertTrue(delta.caughtUp());
        assertEquals(3, delta.changeCount());
        assertEquals(volume.revision(), delta.toRevision());

        WaterVolumeChunk merged = new WaterVolumeChunk();
        merged.applyNetworkSnapshot(
                delta.toRevision(),
                WaterVolumeChunk.mergeNetworkDelta(baseline, delta.upsertData(), delta.tombstones())
        );
        assertFalse(merged.contains(first));
        assertEquals(volume.get(second), merged.get(second));
    }

    @Test
    void expiredDeltaHistoryRequiresAPagedBaseline() {
        WaterVolumeChunk volume = new WaterVolumeChunk();
        BlockPos pos = new BlockPos(0, 64, 0);
        for (int revision = 0; revision <= WaterVolumeChunk.MAX_DELTA_HISTORY; revision++) {
            volume.set(pos, WaterVolumeChunk.WaterCell.still((revision & 1) == 0 ? 1_024 : 2_048, 0));
        }

        assertFalse(volume.deltaSince(0L, 32).available());
    }
}
