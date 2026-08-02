package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void sparseSurfaceRetainsHorizontalCurrentForRendering() {
        WaterVolumeChunk sparse = new WaterVolumeChunk();
        sparse.set(new BlockPos(4, 70, 5), new WaterVolumeChunk.WaterCell(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                0.75f,
                -0.1f,
                -1.25f,
                0,
                WaterVolumeChunk.WaterCell.DEFAULT_TEMPERATURE_MILLI_KELVIN
        ));

        ClientWaterChunkSnapshot snapshot = new ClientWaterChunkSnapshot(
                0, 0, null, sparse.revision(), sparse.toNetworkArray());

        ClientWaterChunkSnapshot.Column column = snapshot.column(4, 5);
        assertEquals(0.75f, column.velocityX(), 1.0e-6f);
        assertEquals(-1.25f, column.velocityZ(), 1.0e-6f);
        assertEquals((float) Math.sqrt(2.125f), column.currentSpeed(), 1.0e-6f);
    }

    @Test
    void displacementReservoirCannotCreatePhantomSurfaceGeometry() {
        WaterVolumeChunk sparse = new WaterVolumeChunk();
        BlockPos reservoir = new BlockPos(6, 72, 7);
        sparse.set(reservoir, new WaterVolumeChunk.WaterCell(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                1.0f,
                0.0f,
                -1.0f,
                WaterVolumeChunk.FLAG_DISPLACEMENT_RESERVOIR,
                WaterVolumeChunk.WaterCell.DEFAULT_TEMPERATURE_MILLI_KELVIN
        ));

        ClientWaterChunkSnapshot snapshot = new ClientWaterChunkSnapshot(
                0, 0, null, sparse.revision(), sparse.toNetworkArray());

        assertFalse(snapshot.contains(6, 72, 7));
        assertEquals(0, snapshot.amountUnits(6, 72, 7));
        assertFalse(snapshot.column(6, 7).wet());
    }

    @Test
    void delayedGeneratedBaselinePreservesPreviouslyPublishedSparseOverrides() {
        BlockPos top = new BlockPos(2, 63, 3);
        WaterVolumeChunk sparse = new WaterVolumeChunk();
        sparse.set(top, WaterVolumeChunk.WaterCell.still(
                0,
                WaterVolumeChunk.FLAG_GENERATED_OVERRIDE | WaterVolumeChunk.FLAG_DRY_OVERRIDE
        ));
        ClientWaterChunkSnapshot sparseOnly = new ClientWaterChunkSnapshot(
                0, 0, null, sparse.revision(), sparse.toNetworkArray());

        ClientWaterChunkSnapshot completed = sparseOnly.withGenerated(generatedColumn().snapshot());

        assertEquals(sparse.revision(), completed.sparseRevision());
        assertFalse(completed.contains(2, 63, 3));
        assertTrue(completed.contains(2, 62, 3));
        assertEquals(62, completed.column(2, 3).surfaceBlockY());
    }

    @Test
    void sparseDeltaAppliesUpsertAndTombstoneOnlyAtTheExpectedRevision() {
        BlockPos removed = new BlockPos(2, 63, 3);
        BlockPos added = new BlockPos(4, 66, 5);
        WaterVolumeChunk server = new WaterVolumeChunk();
        server.set(removed, WaterVolumeChunk.WaterCell.still(2_048, 0));
        long baselineRevision = server.revision();
        ClientWaterChunkSnapshot baseline = new ClientWaterChunkSnapshot(
                0, 0, null, baselineRevision, server.toNetworkArray());

        server.set(removed, WaterVolumeChunk.WaterCell.EMPTY);
        server.set(added, WaterVolumeChunk.WaterCell.still(3_072, 0));
        WaterVolumeChunk.DeltaSnapshot delta = server.deltaSince(baselineRevision, 16);
        ClientWaterChunkSnapshot updated = baseline.withSparseDelta(
                delta.fromRevision(), delta.toRevision(), delta.upsertData(), delta.tombstones());

        assertFalse(updated.contains(2, 63, 3));
        assertEquals(3_072, updated.amountUnits(4, 66, 5));
        assertNull(baseline.withSparseDelta(
                baselineRevision + 1L,
                delta.toRevision() + 1L,
                delta.upsertData(),
                delta.tombstones()
        ));
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
