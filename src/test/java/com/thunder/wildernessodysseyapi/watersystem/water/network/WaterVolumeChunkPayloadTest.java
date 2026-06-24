package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies exact canonical snapshots are split only at cell boundaries. */
class WaterVolumeChunkPayloadTest {

    @Test
    void largeSnapshotIsPagedWithoutTruncation() {
        int firstPageInts = WaterVolumeChunkPayload.MAX_CELLS_PER_PAGE
                * WaterVolumeChunk.SERIALIZED_CELL_STRIDE;
        int[] completeData = new int[firstPageInts + WaterVolumeChunk.SERIALIZED_CELL_STRIDE];
        for (int index = 0; index < completeData.length; index++) {
            completeData[index] = index;
        }

        List<WaterVolumeChunkPayload> pages = WaterVolumeChunkPayload.pagesFromData(
                -3,
                7,
                42L,
                completeData
        );

        assertEquals(2, pages.size());
        assertEquals(0, pages.getFirst().pageIndex());
        assertEquals(1, pages.getLast().pageIndex());
        assertEquals(2, pages.getFirst().pageCount());

        int[] reassembled = new int[completeData.length];
        int offset = 0;
        for (WaterVolumeChunkPayload page : pages) {
            int[] pageData = page.cellData();
            System.arraycopy(pageData, 0, reassembled, offset, pageData.length);
            offset += pageData.length;
        }
        assertArrayEquals(completeData, reassembled);
    }
}
