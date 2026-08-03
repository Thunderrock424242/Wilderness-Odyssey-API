package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
