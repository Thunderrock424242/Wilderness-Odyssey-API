package com.thunder.wildernessodysseyapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the performance config migration is exact, reversible, and non-destructive. */
class PerformanceConfigMigrationTest {

    @TempDir
    Path configDirectory;

    @Test
    void prefixesOnlyKnownLegacySections() {
        String source = """
                # Preserve comments and unknown sections.
                [backgroundEfficiency]
                enabled = false
                  [backgroundEfficiency.scheduler]
                maxTasksPerTick = 12
                [tickEngine.pressure]
                busy = 31.0
                [dataEngine]
                tickBudgetMs = 1.5
                [unrelated]
                enabled = true
                """;

        String migrated = PerformanceConfigMigration.prefixLegacySections(source);

        assertTrue(migrated.contains("[performance.backgroundEfficiency]"));
        assertTrue(migrated.contains("  [performance.backgroundEfficiency.scheduler]"));
        assertTrue(migrated.contains("[performance.tickEngine.pressure]"));
        assertTrue(migrated.contains("[performance.dataEngine]"));
        assertTrue(migrated.contains("[unrelated]"));
        assertFalse(migrated.contains("[performance.unrelated]"));
    }

    @Test
    void createsUnifiedFileAndPreservesEveryLegacyFile() throws IOException {
        Path background = writeLegacy(
                "wildernessodysseyapi-background-efficiency-server.toml",
                "[backgroundEfficiency.scheduler]\nmaxTasksPerTick = 27\n"
        );
        Path tick = writeLegacy(
                "wildernessodysseyapi-tick-engine-server.toml",
                "[tickEngine]\nenabled = false\n"
        );
        Path data = writeLegacy(
                "wildernessodysseyapi-data-engine-server.toml",
                "[dataEngine]\ntickBudgetMs = 1.25\n"
        );

        PerformanceConfigMigration.MigrationResult result =
                PerformanceConfigMigration.prepare(configDirectory);

        Path unified = configDirectory.resolve(PerformanceServerConfig.FILE_NAME);
        String contents = Files.readString(unified);
        assertEquals(PerformanceConfigMigration.MigrationResult.MIGRATED, result);
        assertTrue(contents.contains("[performance]\nenabled = true"));
        assertTrue(contents.contains("[performance.backgroundEfficiency.scheduler]"));
        assertTrue(contents.contains("maxTasksPerTick = 27"));
        assertTrue(contents.contains("[performance.tickEngine]"));
        assertTrue(contents.contains("[performance.dataEngine]"));
        assertTrue(Files.exists(background));
        assertTrue(Files.exists(tick));
        assertTrue(Files.exists(data));
    }

    @Test
    void existingUnifiedFileIsNeverOverwritten() throws IOException {
        Path unified = configDirectory.resolve(PerformanceServerConfig.FILE_NAME);
        Files.writeString(unified, "# current administrator-owned settings\n");
        writeLegacy(
                "wildernessodysseyapi-tick-engine-server.toml",
                "[tickEngine]\nenabled = false\n"
        );

        PerformanceConfigMigration.MigrationResult result =
                PerformanceConfigMigration.prepare(configDirectory);

        assertEquals(PerformanceConfigMigration.MigrationResult.ALREADY_PRESENT, result);
        assertEquals("# current administrator-owned settings\n", Files.readString(unified));
    }

    @Test
    void absentLegacyFilesLeaveDirectoryUntouched() {
        PerformanceConfigMigration.MigrationResult result =
                PerformanceConfigMigration.prepare(configDirectory);

        assertEquals(PerformanceConfigMigration.MigrationResult.NO_LEGACY_FILES, result);
        assertFalse(Files.exists(configDirectory.resolve(PerformanceServerConfig.FILE_NAME)));
    }

    private Path writeLegacy(String fileName, String contents) throws IOException {
        Path path = configDirectory.resolve(fileName);
        Files.writeString(path, contents);
        return path;
    }
}
