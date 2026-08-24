package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Safely assembles the first unified performance config from legacy files. */
public final class PerformanceConfigMigration {
    static final List<String> LEGACY_FILES = List.of(
            "wildernessodysseyapi-background-efficiency-server.toml",
            "wildernessodysseyapi-tick-engine-server.toml",
            "wildernessodysseyapi-data-engine-server.toml"
    );

    private static final List<String> LEGACY_ROOTS = List.of(
            "backgroundEfficiency",
            "tickEngine",
            "dataEngine"
    );

    private PerformanceConfigMigration() {
    }

    /**
     * Creates the unified file only when it is absent and legacy input exists.
     * Existing and legacy files are never overwritten, renamed, or deleted.
     */
    public static MigrationResult prepare(Path requestedConfigDirectory) {
        Path configDirectory = requestedConfigDirectory.toAbsolutePath().normalize();
        Path destination = configDirectory.resolve(PerformanceServerConfig.FILE_NAME).normalize();
        if (!destination.getParent().equals(configDirectory)) {
            return MigrationResult.INVALID_DIRECTORY;
        }
        if (Files.exists(destination)) {
            return Files.isRegularFile(destination)
                    ? MigrationResult.ALREADY_PRESENT
                    : MigrationResult.INVALID_DIRECTORY;
        }

        List<Path> sources = new ArrayList<>();
        for (String legacyFile : LEGACY_FILES) {
            Path source = configDirectory.resolve(legacyFile).normalize();
            if (source.getParent().equals(configDirectory) && Files.isRegularFile(source)) {
                sources.add(source);
            }
        }
        if (sources.isEmpty()) {
            return MigrationResult.NO_LEGACY_FILES;
        }

        Path temporary = null;
        try {
            Files.createDirectories(configDirectory);
            temporary = Files.createTempFile(configDirectory, "performance-config-", ".tmp");
            Files.writeString(
                    temporary,
                    migratedContents(sources),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveIntoPlace(temporary, destination);
            ModConstants.LOGGER.info(
                    "[Config] Migrated {} legacy performance config file(s) into {}; sources remain untouched",
                    sources.size(),
                    destination
            );
            return MigrationResult.MIGRATED;
        } catch (IOException | SecurityException exception) {
            ModConstants.LOGGER.error(
                    "[Config] Could not create unified performance config {}; legacy files remain untouched",
                    destination,
                    exception
            );
            return MigrationResult.FAILED;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The temporary file was created by this migration and is safe to leave for diagnosis.
                }
            }
        }
    }

    static String prefixLegacySections(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder migrated = new StringBuilder(normalized.length() + 128);
        for (String line : normalized.split("\n", -1)) {
            String leading = line.stripLeading();
            int leadingLength = line.length() - leading.length();
            String replacement = prefixHeader(leading);
            migrated.append(line, 0, leadingLength).append(replacement).append('\n');
        }
        return migrated.toString();
    }

    private static String migratedContents(List<Path> sources) throws IOException {
        StringBuilder result = new StringBuilder(16_384);
        result.append("# Unified Wilderness Odyssey performance configuration.\n")
                .append("# Migrated legacy files are preserved beside this file as rollback backups.\n\n")
                .append("[performance]\n")
                .append("enabled = true\n\n");
        for (Path source : sources) {
            result.append("# Migrated from ").append(source.getFileName()).append("\n")
                    .append(prefixLegacySections(Files.readString(source, StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return result.toString();
    }

    private static String prefixHeader(String line) {
        if (!line.startsWith("[") || line.startsWith("[[") || !line.endsWith("]")) {
            return line;
        }
        String path = line.substring(1, line.length() - 1).trim();
        for (String root : LEGACY_ROOTS) {
            if (path.equals(root) || path.startsWith(root + ".")) {
                return "[performance." + path + "]";
            }
        }
        return line;
    }

    private static void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination);
        }
    }

    /** Outcome of preparing the legacy performance destination. */
    public enum MigrationResult {
        MIGRATED,
        ALREADY_PRESENT,
        NO_LEGACY_FILES,
        INVALID_DIRECTORY,
        FAILED
    }
}
