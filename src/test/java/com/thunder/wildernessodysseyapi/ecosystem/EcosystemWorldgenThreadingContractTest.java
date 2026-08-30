package com.thunder.wildernessodysseyapi.ecosystem;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards spawn-position checks from consuming shared level randomness on worldgen workers. */
class EcosystemWorldgenThreadingContractTest {

    private static final Path PROJECT = Path.of(
            System.getProperty("wildernessodysseyapi.projectDir", ".")
    );

    @Test
    void wildlifePositionCheckKeepsWorldgenOffRuntimeStateAndSharedRandom() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/ecosystem/EcosystemEvents.java"
        ));
        int methodStart = source.indexOf("public static void onWildlifePositionCheck");
        int methodEnd = source.indexOf("public static void onPlayerLogout", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart, "Wildlife position-check handler is missing");

        String handler = source.substring(methodStart, methodEnd);
        assertTrue(handler.contains("event.getSpawnType() != MobSpawnType.NATURAL"),
                "The runtime disturbance policy must be limited to natural spawns");
        assertFalse(handler.contains("MobSpawnType.CHUNK_GENERATION"),
                "Worldgen workers must return before environmental-memory access");
        assertTrue(handler.contains("animal.getRandom().nextDouble()"),
                "Runtime spawn rolls must use the candidate mob's private random source");
        assertFalse(handler.contains("level.getRandom()"),
                "Runtime spawn rolls must not consume the shared ServerLevel random source");
    }
}
