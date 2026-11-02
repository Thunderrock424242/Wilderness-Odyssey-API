package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Guards the narrow block-height filter used to requeue physical surface meshes. */
class ClientWaterSurfaceInvalidationTest {

    @Test
    void detailRefreshDeduplicatesWithoutInvalidatingNeighborsOrMissingChunks() {
        try {
            ClientWaterSnapshotStore.publishSparse(null, 1000, 1000, 1, new int[0]);
            ClientWaterSnapshotStore.publishSparse(null, 1001, 1000, 1, new int[0]);
            while (ClientWaterSnapshotStore.pollDirtyMesh() != null) { }
            ClientWaterSnapshotStore.queueMeshDetailRefresh(null, 1000, 1000);
            ClientWaterSnapshotStore.queueMeshDetailRefresh(null, 1000, 1000);
            ClientWaterSnapshotStore.queueMeshDetailRefresh(null, 7, 7);
            assertEquals(1, ClientWaterSnapshotStore.pendingDirtyMeshCount());
            assertEquals(ChunkPos.asLong(1000, 1000), ClientWaterSnapshotStore.pollDirtyMesh());
            assertNull(ClientWaterSnapshotStore.pollDirtyMesh());
        } finally {
            ClientWaterSnapshotStore.remove(null, 1000, 1000);
            ClientWaterSnapshotStore.remove(null, 1001, 1000);
            while (ClientWaterSnapshotStore.pollDirtyMesh() != null) { }
        }
    }

    @Test
    void invalidatesTheSurfaceCellAndItsCoverButNotDeepTerrain() {
        ClientWaterChunkSnapshot.Column ocean = new ClientWaterChunkSnapshot.Column(
                true, 62, 40, 8, 255, 0, 0, 0.0f, 0.0f,
                GeneratedWaterChunk.BodyType.OCEAN,
                GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT,
                false
        );

        assertTrue(ClientWaterSnapshotStore.affectsRenderedSurface(ocean, 62));
        assertTrue(ClientWaterSnapshotStore.affectsRenderedSurface(ocean, 63));
        assertFalse(ClientWaterSnapshotStore.affectsRenderedSurface(ocean, 61));
        assertFalse(ClientWaterSnapshotStore.affectsRenderedSurface(
                ClientWaterChunkSnapshot.Column.DRY, 62));
    }
}
