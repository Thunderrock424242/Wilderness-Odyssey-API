package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards physical exposure and client invalidation for snapshot water meshes. */
class WaterSurfaceTopologyContractTest {

    private static final Path PROJECT = Path.of(
            System.getProperty("wildernessodysseyapi.projectDir", ".")
    );

    @Test
    void generatedMetadataCannotRenderAcrossAnExcavatedDrySurface() {
        ClientWaterChunkSnapshot.Column ocean = exposedOcean();

        assertTrue(WaterChunkMeshCache.isLiveSurfaceEligible(
                ocean, true, false, false, true));
        assertFalse(WaterChunkMeshCache.isLiveSurfaceEligible(
                ocean, false, false, false, true),
                "A dry physical cell must mask stale generated water metadata");
        assertFalse(WaterChunkMeshCache.isLiveSurfaceEligible(
                ocean, true, true, false, true),
                "A submerged cell is not the top surface");
        assertFalse(WaterChunkMeshCache.isLiveSurfaceEligible(
                ocean, true, false, true, false),
                "A terrain-covered top must remain on the baked fallback path");
    }

    @Test
    void clientChunkWritesAreRegisteredAsSurfaceInvalidationHooks() throws IOException {
        String mixin = Files.readString(PROJECT.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/mixin/"
                        + "ClientLevelChunkWaterMeshMixin.java"
        ));
        String config = Files.readString(PROJECT.resolve(
                "src/main/resources/mixins.wildernessodysseyapi.json"
        ));

        assertTrue(mixin.contains("@Mixin(LevelChunk.class)"));
        assertTrue(mixin.contains("method = \"setBlockState("));
        assertTrue(mixin.contains("at = @At(\"RETURN\")"));
        assertTrue(mixin.contains("ClientWaterSnapshotStore.notifyBlockChange"));
        assertTrue(config.contains("\"ClientLevelChunkWaterMeshMixin\""));
    }

    @Test
    void uploadedOwnershipMaskContainsOnlyPhysicallyVerifiedColumns() {
        int localX = 5;
        int localZ = 9;
        int columnIndex = localX | (localZ << 4);
        long verifiedWord = 1L << (columnIndex & 63);
        WaterChunkMeshCache.MeshGroup group = new WaterChunkMeshCache.MeshGroup(
                0L, 0L, 0L, 0, 0, null,
                new AABB(0, 0, 0, 1, 1, 1),
                4, 2,
                0L, 0L, verifiedWord, 0L
        );

        assertTrue(group.ownsSurface(localX, localZ));
        assertFalse(group.ownsSurface(localX + 1, localZ));
    }

    @Test
    void shorelineContinuityAnchorsBoundaryAndRestoresWavesInland() {
        WaterChunkMeshCache.ColumnEligibility straightShore = (worldX, worldZ) -> worldX >= 0;

        float boundary = WaterChunkMeshCache.displacementContinuityAt(0, 8, straightShore);
        float firstInteriorRow = WaterChunkMeshCache.displacementContinuityAt(1, 8, straightShore);
        float secondInteriorRow = WaterChunkMeshCache.displacementContinuityAt(2, 8, straightShore);

        assertEquals(WaterChunkMeshCache.BOUNDARY_DISPLACEMENT_CONTINUITY, boundary);
        assertEquals(WaterChunkMeshCache.SHORE_RAMP_DISPLACEMENT_CONTINUITY, firstInteriorRow);
        assertEquals(WaterChunkMeshCache.OPEN_WATER_DISPLACEMENT_CONTINUITY, secondInteriorRow);
        assertEquals(0.0f, WaterSurfaceEquation.surfaceContinuityFactor(boundary), 0.0f,
                "Boundary payload must suppress waves, horizontal chop, wakes, and tide exactly");
        assertEquals(0.5f, WaterSurfaceEquation.surfaceContinuityFactor(firstInteriorRow), 1.0e-6f);
        assertEquals(1.0f, WaterSurfaceEquation.surfaceContinuityFactor(secondInteriorRow), 0.0f);
    }

    @Test
    void missingSnapshotFrontierUsesTheSameWorldVertexAnchorFromEitherChunk() {
        WaterChunkMeshCache.ColumnEligibility loadedWater = (worldX, worldZ) ->
                worldX >= 0 && worldX < 16 && worldZ >= 0 && worldZ < 16;

        float eastFrontier = WaterChunkMeshCache.displacementContinuityAt(16, 7, loadedWater);
        float southEastCorner = WaterChunkMeshCache.displacementContinuityAt(16, 16, loadedWater);

        assertEquals(WaterChunkMeshCache.BOUNDARY_DISPLACEMENT_CONTINUITY, eastFrontier);
        assertEquals(WaterChunkMeshCache.BOUNDARY_DISPLACEMENT_CONTINUITY, southEastCorner);
    }

    @Test
    void diagonalDryColumnAnchorsTheSharedCorner() {
        WaterChunkMeshCache.ColumnEligibility diagonalShore = (worldX, worldZ) ->
                worldX != -1 || worldZ != -1;

        assertEquals(
                WaterChunkMeshCache.BOUNDARY_DISPLACEMENT_CONTINUITY,
                WaterChunkMeshCache.displacementContinuityAt(0, 0, diagonalShore)
        );
    }

    private static ClientWaterChunkSnapshot.Column exposedOcean() {
        return new ClientWaterChunkSnapshot.Column(
                true,
                62,
                40,
                8,
                255,
                0,
                0,
                0.0f,
                0.0f,
                GeneratedWaterChunk.BodyType.OCEAN,
                GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT,
                false
        );
    }
}
