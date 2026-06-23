package com.thunder.wildernessodysseyapi.modlisttracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that config cleanup removes only files with one confident absent-mod owner.
 */
class ConfigCleanupServiceTest {

    @TempDir
    Path configDir;

    @Test
    void scanFindsStaleConfigsWithoutDeletingThem() throws IOException {
        Path active = write("create-client.toml");
        Path stale = write("removedmod-common.toml");
        Path nestedStale = write("removedmod/options.json");
        Path unknown = write("pack-settings.json");

        ConfigCleanupService.CleanupResult result = ConfigCleanupService.execute(
                configDir, Set.of("create"), Set.of("create", "removedmod"), false);

        assertEquals("create", result.activeConfigs().get("create-client.toml"));
        assertEquals("removedmod", result.staleConfigCandidates().get("removedmod-common.toml"));
        assertEquals("removedmod", result.staleConfigCandidates().get("removedmod/options.json"));
        assertTrue(result.unresolvedConfigs().contains("pack-settings.json"));
        assertTrue(Files.exists(active));
        assertTrue(Files.exists(stale));
        assertTrue(Files.exists(nestedStale));
        assertTrue(Files.exists(unknown));
        assertTrue(result.deletedConfigs().isEmpty());
    }

    @Test
    void cleanDeletesStaleConfigsAndRetainsUnknownAndActiveFiles() throws IOException {
        Path active = write("create-common.toml");
        Path stale = write("removedmod/settings.cfg");
        Path unknown = write("custom-server.properties");

        ConfigCleanupService.CleanupResult result = ConfigCleanupService.execute(
                configDir, Set.of("create"), Set.of("create", "removedmod"), true);

        assertTrue(result.deletedConfigs().contains("removedmod/settings.cfg"));
        assertFalse(Files.exists(stale));
        assertFalse(Files.exists(configDir.resolve("removedmod")));
        assertTrue(Files.exists(active));
        assertTrue(Files.exists(unknown));
        assertTrue(result.failedDeletions().isEmpty());
    }

    @Test
    void cleanSkipsOwnershipMatchesThatAreAmbiguous() throws IOException {
        Path ambiguous = write("foo_bar-common.toml");

        ConfigCleanupService.CleanupResult result = ConfigCleanupService.execute(
                configDir, Set.of(), Set.of("foo", "foo_bar"), true);

        assertEquals(Set.of("foo", "foo_bar"), Set.copyOf(result.ambiguousConfigs().get("foo_bar-common.toml")));
        assertTrue(result.staleConfigCandidates().isEmpty());
        assertTrue(result.deletedConfigs().isEmpty());
        assertTrue(Files.exists(ambiguous));
    }

    @Test
    void unsupportedFilesAreNeverCleanupCandidates() throws IOException {
        Path notes = write("removedmod/notes.md");

        ConfigCleanupService.CleanupResult result = ConfigCleanupService.execute(
                configDir, Set.of(), Set.of("removedmod"), true);

        assertEquals(0, result.totalConfigFiles());
        assertTrue(Files.exists(notes));
    }

    private Path write(String relativePath) throws IOException {
        Path path = configDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "test=true");
    }
}
