package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureInspectionReport;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureInspector;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in regression coverage for the tracked multi-million-block bunker fixture.
 *
 * <p>The production reader intentionally materializes every block so this test needs the dedicated
 * four-gigabyte fixture task. Ordinary focused unit tests skip it with an explicit assumption.</p>
 */
class BunkerStructureRegressionTest {

    private static final String EXPECTED_SHA_256 =
            "81d61f883d056fc9bb8d9f0ac7bdc3e653cba9b23d1fb468844cf3e4331e6900";

    @Test
    void readsAndInspectsTrackedBunkerWithoutMutatingIt() throws IOException, NoSuchAlgorithmException {
        Assumptions.assumeTrue(
                Boolean.getBoolean("structuregen.runBunkerRegression"),
                "Enable with -Dstructuregen.runBunkerRegression=true under the dedicated 4g fixture task"
        );
        Path bunker = projectRoot().resolve(
                "src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt"
        );
        assertTrue(Files.isRegularFile(bunker), "Tracked bunker fixture is missing: " + bunker);
        String beforeRead = sha256(bunker);
        assertEquals(EXPECTED_SHA_256, beforeRead, "Tracked bunker bytes changed before the read-only regression");

        try {
            StructureModel model = new MinecraftStructureNbtReader().read(bunker, "bunker");
            StructureInspectionReport report = new StructureInspector().inspect(bunker, model);

            assertEquals("bunker", model.name());
            assertEquals(3955, report.dataVersion());
            assertEquals(new StructureSize(157, 76, 182), report.size());
            assertEquals(2_171_624L, report.boundingVolume());
            assertEquals(2_171_624, report.storedBlocks());
            assertEquals(838_493, report.explicitAirBlocks());
            assertEquals(1_333_131, report.occupiedBlocks());
            assertEquals(1, report.paletteCount());
            assertEquals(883, report.primaryPaletteSize());
            assertEquals(270, report.uniqueBlockTypes());
            assertEquals(883, report.blockStateVariants());
            assertEquals(6_307, report.blockEntityCount());
            assertEquals(68, report.entityCount());
            assertTrue(report.unknownOrUnsupportedTags().isEmpty());
            assertEquals(Map.of(
                    "minecraft:creeper", 18L,
                    "create:super_glue", 16L,
                    "minecraft:spider", 13L,
                    "minecraft:zombie", 13L,
                    "minecraft:skeleton", 5L,
                    "minecraft:armor_stand", 2L,
                    "minecraft:enderman", 1L
            ), report.entityTypes());
        } finally {
            assertEquals(beforeRead, sha256(bunker), "The read-only bunker regression modified the fixture");
        }
    }

    private Path projectRoot() {
        return Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir",
                System.getProperty("user.dir")
        )).toAbsolutePath().normalize();
    }

    private String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
