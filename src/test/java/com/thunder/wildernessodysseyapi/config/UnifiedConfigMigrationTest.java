package com.thunder.wildernessodysseyapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that three-file migration preserves values and every legacy source. */
class UnifiedConfigMigrationTest {

    @TempDir
    Path configDirectory;

    @Test
    void prefixesSubsystemCategoriesAndNestedTables() {
        String source = """
                # Existing operator values.
                [localized_clouds]
                enabled = false
                  [localized_clouds.quality]
                renderDistanceBlocks = 128
                """;

        String migrated = UnifiedConfigMigration.prefixSections(
                source,
                "weather_rendering"
        );

        assertTrue(migrated.startsWith("[weather_rendering]\n"));
        assertTrue(migrated.contains("[weather_rendering.localized_clouds]"));
        assertTrue(migrated.contains("  [weather_rendering.localized_clouds.quality]"));
        assertTrue(migrated.contains("enabled = false"));
        assertTrue(migrated.contains("renderDistanceBlocks = 128"));
    }

    @Test
    void createsThreeFilesAndLeavesLegacySourcesUntouched() throws IOException {
        Path structures = writeLegacy(
                "wildernessodysseyapi-structures.toml",
                "[placement]\nenableAutoTerrainBlend = false\n"
        );
        Path donations = writeLegacy(
                "wildernessodysseyapi-donations-client.toml",
                "disableReminder = true\noptOutReleaseVersion = \"1.2.3\"\n"
        );
        Path weather = writeLegacy(
                "wildernessodysseyapi-weather-server.toml",
                "[weather]\nenabled = false\n[weather.severe]\nblockDamageEnabled = true\n"
        );

        UnifiedConfigMigration.MigrationResult result = UnifiedConfigMigration.prepare(configDirectory);

        assertEquals(UnifiedConfigMigration.MigrationResult.MIGRATED, result);
        String common = Files.readString(configDirectory.resolve(WildernessConfigSpecs.COMMON_FILE));
        String client = Files.readString(configDirectory.resolve(WildernessConfigSpecs.CLIENT_FILE));
        String server = Files.readString(configDirectory.resolve(WildernessConfigSpecs.SERVER_FILE));
        assertTrue(common.contains("[structures.placement]"));
        assertTrue(common.contains("enableAutoTerrainBlend = false"));
        assertTrue(client.contains("[donations]"));
        assertTrue(client.contains("optOutReleaseVersion = \"1.2.3\""));
        assertTrue(server.contains("[weather]"));
        assertTrue(server.contains("[weather.severe]"));
        assertTrue(server.contains("blockDamageEnabled = true"));
        assertTrue(Files.exists(structures));
        assertTrue(Files.exists(donations));
        assertTrue(Files.exists(weather));
    }

    @Test
    void existingUnifiedDestinationsAreNeverOverwritten() throws IOException {
        Path destination = configDirectory.resolve(WildernessConfigSpecs.COMMON_FILE);
        Files.writeString(destination, "# administrator-owned common settings\n");
        writeLegacy("wildernessodysseyapi-structures.toml", "[placement]\nmaxLevelingDepth = 2\n");

        UnifiedConfigMigration.MigrationResult result = UnifiedConfigMigration.prepare(configDirectory);

        assertEquals(UnifiedConfigMigration.MigrationResult.ALREADY_PRESENT, result);
        assertEquals("# administrator-owned common settings\n", Files.readString(destination));
    }

    @Test
    void directoryAtUnifiedDestinationIsRejected() throws IOException {
        Files.createDirectory(configDirectory.resolve(WildernessConfigSpecs.SERVER_FILE));

        UnifiedConfigMigration.MigrationResult result = UnifiedConfigMigration.prepare(configDirectory);

        assertEquals(UnifiedConfigMigration.MigrationResult.INVALID_DIRECTORY, result);
    }

    @Test
    void absentLegacyFilesDoNotCreateMigrationOutputs() {
        UnifiedConfigMigration.MigrationResult result = UnifiedConfigMigration.prepare(configDirectory);

        assertEquals(UnifiedConfigMigration.MigrationResult.NO_LEGACY_FILES, result);
        assertFalse(Files.exists(configDirectory.resolve(WildernessConfigSpecs.COMMON_FILE)));
        assertFalse(Files.exists(configDirectory.resolve(WildernessConfigSpecs.CLIENT_FILE)));
        assertFalse(Files.exists(configDirectory.resolve(WildernessConfigSpecs.SERVER_FILE)));
    }

    private Path writeLegacy(String fileName, String contents) throws IOException {
        Path path = configDirectory.resolve(fileName);
        Files.writeString(path, contents);
        return path;
    }
}
