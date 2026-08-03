package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the narrow block-height filter used to requeue physical surface meshes. */
class ClientWaterSurfaceInvalidationTest {

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
