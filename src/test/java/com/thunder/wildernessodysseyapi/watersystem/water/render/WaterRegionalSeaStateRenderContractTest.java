package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards world-anchored, shared-corner sea-state uploads for snapshot meshes. */
class WaterRegionalSeaStateRenderContractTest {

    private static final Path PROJECT = Path.of(
            System.getProperty("wildernessodysseyapi.projectDir", ".")
    );

    @Test
    void snapshotGroupsUseCachedWorldCornersInsteadOfCameraMaterialState() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/render/"
                        + "WaterRenderCoordinator.java"
        ));

        assertTrue(source.contains("WaterShaders.beginRegionalOceanStatePass()"));
        assertTrue(source.contains("WaterShaders.uploadRegionalOceanState("));
        assertTrue(source.contains("minimumX + 16, minimumZ, partialTick"));
        assertTrue(source.contains("minimumX, minimumZ + 16, partialTick"));
        assertTrue(source.contains("minimumX + 16, minimumZ + 16, partialTick"));
        assertTrue(source.contains("REGIONAL_SEA_CORNERS.computeIfAbsent("));
        assertTrue(source.contains("level, worldX, worldZ, partialTick"));
    }
}
