package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void legacyLedgerIncludesAnOriginalStatePaletteEntry() {
        TemporaryFloodSavedData data = new TemporaryFloodSavedData();
        BlockPos position = new BlockPos(4, 65, 9);

        assertTrue(data.record(position, 9L, 30L, 16));
        assertEquals(1, data.save(new CompoundTag(), null)
                .getList("original_states", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
    }

    @Test
    void versionThreeLedgerPreservesStandingWaterKindAndCounts() {
        TemporaryFloodSavedData data = new TemporaryFloodSavedData();
        BlockPos pond = new BlockPos(20, 63, -2);

        assertTrue(data.record(
                pond, 9L, 30L, 16, null, SurfaceWaterKind.RAIN_POND
        ));
        CompoundTag encoded = data.save(new CompoundTag(), null);
        TemporaryFloodSavedData decoded = TemporaryFloodSavedData.load(encoded, null);
        long chunkKey = ChunkPos.asLong(1, -1);

        assertEquals(SurfaceWaterKind.RAIN_POND, decoded.kind(pond.asLong()));
        assertEquals(1, decoded.standingWaterCountInChunk(chunkKey));
        assertEquals(SurfaceWaterKind.RAIN_POND, decoded.dominantStandingKind(chunkKey));
    }

    @Test
    void quantityOwnershipSplitsAndPersistsWithoutCreatingUnits() {
        TemporaryFloodSavedData data = new TemporaryFloodSavedData();
        BlockPos source = new BlockPos(1, 64, 1);
        BlockPos target = new BlockPos(2, 64, 1);
        assertTrue(data.record(
                source, 77L, 10L, 16, null, SurfaceWaterKind.FLOOD, 3_072
        ));

        assertTrue(data.transferOwnedUnits(source, target, 1_024, 11L, 16, null));
        assertEquals(2_048, data.ownedUnits(source.asLong()));
        assertEquals(1_024, data.ownedUnits(target.asLong()));

        TemporaryFloodSavedData decoded = TemporaryFloodSavedData.load(
                data.save(new CompoundTag(), null), null
        );
        assertEquals(3_072,
                decoded.ownedUnits(source.asLong()) + decoded.ownedUnits(target.asLong()));
    }

    @Test
    void quantityTransferRejectsOverdrawAndLeavesBothClaimsUnchanged() {
        TemporaryFloodSavedData data = new TemporaryFloodSavedData();
        BlockPos source = new BlockPos(1, 64, 1);
        BlockPos target = new BlockPos(2, 64, 1);
        assertTrue(data.record(
                source, 77L, 10L, 16, null, SurfaceWaterKind.FLOOD, 512
        ));

        assertFalse(data.transferOwnedUnits(source, target, 513, 11L, 16, null));
        assertEquals(512, data.ownedUnits(source.asLong()));
        assertEquals(0, data.ownedUnits(target.asLong()));
    }

    @Test
    void fundedParcelKeepsItsSourceAcrossChunkMovementReloadAndExternalWithdrawal() {
        var data = new TemporaryFloodSavedData();
        var source = new BlockPos(15, 64, 0);
        var target = new BlockPos(16, 64, 0);
        long funding = ChunkPos.asLong(0, 0);
        assertTrue(data.record(source, 77, 10, 16, null, SurfaceWaterKind.FLOOD, 4096, funding));
        assertTrue(data.transferOwnedUnits(source, target, 1024, 11, 16, null));
        var loaded = TemporaryFloodSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(funding, loaded.fundingRegion(target.asLong()));
        assertEquals(4096000, loaded.ownedMilliUnits(funding));
        assertEquals(512, loaded.releaseOwnedUnits(target, 512));
        assertEquals(3584000, loaded.ownedMilliUnits(funding));
        assertFalse(loaded.transferOwnedUnits(source, source, 1, 12, 16, null));
    }

    @Test
    void legacyWetlandMigratesHalfBlockAndFutureLedgersAreRejected() {
        var data = new TemporaryFloodSavedData();
        var position = new BlockPos(0, 64, 0);
        data.record(position, 9, 0, 16, null, SurfaceWaterKind.WETLAND);
        var tag = data.save(new CompoundTag(), null);
        tag.putInt("version", 3);
        tag.remove("owned_units");
        tag.remove("funding_regions");
        var migrated = TemporaryFloodSavedData.load(tag, null);
        assertEquals(2048, migrated.ownedUnits(position.asLong()));
        assertEquals(TemporaryFloodSavedData.LEGACY_FUNDING, migrated.fundingRegion(position.asLong()));
        tag.putInt("version", 99);
        assertThrows(IllegalArgumentException.class, () -> TemporaryFloodSavedData.load(tag, null));
    }
}
