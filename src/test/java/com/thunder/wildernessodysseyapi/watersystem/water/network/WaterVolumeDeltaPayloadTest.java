package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies bounded revision metadata for sparse upserts and tombstones. */
class WaterVolumeDeltaPayloadTest {

    @Test
    void payloadRetainsContiguousRevisionRangeAndDefensiveArrays() {
        WaterVolumeChunk volume = new WaterVolumeChunk();
        BlockPos pos = new BlockPos(1, 64, 1);
        volume.set(pos, WaterVolumeChunk.WaterCell.still(1_024, 0));
        long fromRevision = volume.revision();
        volume.set(pos, WaterVolumeChunk.WaterCell.EMPTY);

        WaterVolumeDeltaPayload payload = WaterVolumeDeltaPayload.from(
                2, -4, volume.deltaSince(fromRevision, 16));
        int[] tombstones = payload.tombstones();
        tombstones[0] = 0;

        assertEquals(fromRevision, payload.fromRevision());
        assertEquals(volume.revision(), payload.toRevision());
        assertEquals(WaterVolumeChunk.pack(pos), payload.tombstones()[0]);
    }

    @Test
    void nonContiguousRevisionMetadataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterVolumeDeltaPayload(
                0, 0, 5L, 7L, 1, new int[0], new int[] { 1 }
        ));
    }
}
