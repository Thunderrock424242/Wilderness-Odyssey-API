package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies generated baseline and sparse runtime precedence in immutable snapshots. */
class ClientWaterChunkSnapshotTest {

    @Test
    void drySparseOverrideWinsOverGeneratedSurface() {
        GeneratedWaterChunk generated = generatedColumn();
        BlockPos top = new BlockPos(2, 63, 3);
        WaterVolumeChunk sparse = new WaterVolumeChunk();
        sparse.set(top, WaterVolumeChunk.WaterCell.still(0,
                WaterVolumeChunk.FLAG_GENERATED_OVERRIDE | WaterVolumeChunk.FLAG_DRY_OVERRIDE));

        ClientWaterChunkSnapshot snapshot = new ClientWaterChunkSnapshot(
                0, 0, generated.snapshot(), sparse.revision(), sparse.toNetworkArray());

        assertFalse(snapshot.contains(2, 63, 3));
        assertTrue(snapshot.contains(2, 62, 3));
        assertEquals(62, snapshot.column(2, 3).surfaceBlockY());
    }

    @Test
    void sparseRuntimeWaterCanRiseAboveGeneratedSurface() {
        GeneratedWaterChunk generated = generatedColumn();
        WaterVolumeChunk sparse = new WaterVolumeChunk();
        sparse.set(new BlockPos(2, 64, 3), WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK / 2,
                WaterVolumeChunk.FLAG_GENERATED_OVERRIDE));

        ClientWaterChunkSnapshot snapshot = new ClientWaterChunkSnapshot(
                0, 0, generated.snapshot(), sparse.revision(), sparse.toNetworkArray());

        assertEquals(64, snapshot.column(2, 3).surfaceBlockY());
        assertEquals(4, snapshot.column(2, 3).amount());
        assertFalse(snapshot.column(2, 3).surfaceCovered());
    }

    @Test
    void generatedSurfaceCoverIsImmutableSnapshotData() {
        GeneratedWaterChunk generated = generatedColumn();
        generated.recordSurfaceCover(new BlockPos(2, 64, 3), true);

        ClientWaterChunkSnapshot snapshot = new ClientWaterChunkSnapshot(
                0, 0, generated.snapshot(), 0L, new int[0]);

        assertTrue(snapshot.column(2, 3).surfaceCovered());
    }

    private static GeneratedWaterChunk generatedColumn() {
        GeneratedWaterChunk generated = new GeneratedWaterChunk();
        for (int y = 58; y <= 63; y++) {
            generated.recordCell(new BlockPos(2, y, 3), GeneratedWaterChunk.Cell.of(
                    8, false, GeneratedWaterChunk.BodyType.OCEAN));
        }
        return generated;
    }
}
