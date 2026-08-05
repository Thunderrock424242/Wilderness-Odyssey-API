package com.thunder.wildernessodysseyapi.watersystem.ocean;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards render-time forwarding into the interpolated client sea-state field. */
class OceanSeaStateRenderContractTest {

    private static final Path PROJECT = Path.of(
            System.getProperty("wildernessodysseyapi.projectDir", ".")
    );

    @Test
    void commonClientSamplingForwardsTheRenderPartialTick() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/watersystem/ocean/"
                        + "OceanSeaState.java"
        ));

        assertTrue(source.contains(
                "ClientOceanSeaState.sampleAt(level, worldX, worldZ, partialTick)"
        ));
    }
}
