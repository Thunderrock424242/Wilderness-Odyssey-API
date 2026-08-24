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

/**
 * Safely creates the three unified config files from the former feature files.
 *
 * <p>Migration is create-only: an existing unified destination always wins,
 * while every legacy source remains untouched as a rollback copy.</p>
 */
public final class UnifiedConfigMigration {
    private static final List<FilePlan> PLANS = List.of(
            new FilePlan(WildernessConfigSpecs.COMMON_FILE, List.of(
                    source("wildernessodysseyapi-structures.toml", "structures"),
                    source("wildernessodysseyapi-async.toml"),
                    source("wildernessodysseyapi-ownership.toml")
            )),
            new FilePlan(WildernessConfigSpecs.CLIENT_FILE, List.of(
                    source("wildernessodysseyapi-donations-client.toml", "donations"),
                    source("wildernessodysseyapi-debug-overlay-client.toml"),
                    source("wildernessodysseyapi-water-rendering-client.toml"),
                    source("wildernessodysseyapi-weather-rendering-client.toml", "weather_rendering")
            )),
            new FilePlan(WildernessConfigSpecs.SERVER_FILE, List.of(
                    source("wildernessodysseyapi-structureblocks-server.toml"),
                    source(PerformanceServerConfig.FILE_NAME),
                    source("wildernessodysseyapi-development-studio-server.toml", "development_studio"),
                    source("wildernessodysseyapi-verification-relay-server.toml"),
                    source("wildernessodysseyapi-telemetry-master-server.toml"),
                    source("wildernessodysseyapi-telemetry-server.toml"),
                    source("wildernessodysseyapi-event-telemetry-server.toml"),
                    source("wildernessodysseyapi-feedback-server.toml"),
                    source("wildernessodysseyapi-riftfall-server.toml"),
                    source("wildernessodysseyapi-meteors-server.toml"),
                    source("wildernessodysseyapi-temporal-rift-server.toml"),
                    source("wildernessodysseyapi-water-simulation-server.toml"),
                    source("wildernessodysseyapi-weather-server.toml"),
                    source("wildernessodysseyapi-ecosystem-server.toml"),
                    source("wildernessodysseyapi-vegetation-server.toml")
            ))
    );

    private UnifiedConfigMigration() {
    }

    /** Creates every missing unified file for which at least one legacy source exists. */
    public static MigrationResult prepare(Path requestedConfigDirectory) {
        Path configDirectory = requestedConfigDirectory.toAbsolutePath().normalize();
        boolean migrated = false;
        boolean alreadyPresent = false;
        for (FilePlan plan : PLANS) {
            FileResult result = prepareFile(configDirectory, plan);
            switch (result) {
                case MIGRATED -> migrated = true;
                case ALREADY_PRESENT -> alreadyPresent = true;
                case INVALID_DIRECTORY -> {
                    return MigrationResult.INVALID_DIRECTORY;
                }
                case FAILED -> {
                    return MigrationResult.FAILED;
                }
                case NO_LEGACY_FILES -> {
                    // NeoForge will create a default file for this side.
                }
            }
        }
        if (migrated) {
            return MigrationResult.MIGRATED;
        }
        return alreadyPresent ? MigrationResult.ALREADY_PRESENT : MigrationResult.NO_LEGACY_FILES;
    }

    private static FileResult prepareFile(Path configDirectory, FilePlan plan) {
        Path destination = configDirectory.resolve(plan.destination()).normalize();
        if (!destination.getParent().equals(configDirectory)) {
            return FileResult.INVALID_DIRECTORY;
        }
        if (Files.exists(destination)) {
            return Files.isRegularFile(destination)
                    ? FileResult.ALREADY_PRESENT
                    : FileResult.INVALID_DIRECTORY;
        }

        List<ResolvedSource> sources = new ArrayList<>();
        for (SourcePlan source : plan.sources()) {
            Path path = configDirectory.resolve(source.fileName()).normalize();
            if (!path.getParent().equals(configDirectory)) {
                return FileResult.INVALID_DIRECTORY;
            }
            if (Files.isRegularFile(path)) {
                sources.add(new ResolvedSource(path, source));
            }
        }
        if (sources.isEmpty()) {
            return FileResult.NO_LEGACY_FILES;
        }

        Path temporary = null;
        try {
            Files.createDirectories(configDirectory);
            temporary = Files.createTempFile(configDirectory, "unified-config-", ".tmp");
            Files.writeString(
                    temporary,
                    migratedContents(plan.destination(), sources),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveIntoPlace(temporary, destination);
            ModConstants.LOGGER.info(
                    "[Config] Migrated {} legacy config file(s) into {}; sources remain untouched",
                    sources.size(),
                    destination
            );
            return FileResult.MIGRATED;
        } catch (IOException | SecurityException exception) {
            ModConstants.LOGGER.error(
                    "[Config] Could not create unified config {}; legacy files remain untouched",
                    destination,
                    exception
            );
            return FileResult.FAILED;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The migration-created temporary file is safe to leave for diagnosis.
                }
            }
        }
    }

    private static String migratedContents(String destination, List<ResolvedSource> sources) throws IOException {
        StringBuilder result = new StringBuilder(64_000);
        result.append("# Unified Wilderness Odyssey configuration: ")
                .append(destination)
                .append('\n')
                .append("# Migrated legacy files remain beside this file as rollback backups.\n\n");
        for (ResolvedSource resolved : sources) {
            result.append("# Migrated from ")
                    .append(resolved.path().getFileName())
                    .append('\n')
                    .append(prefixSections(
                            Files.readString(resolved.path(), StandardCharsets.UTF_8),
                            resolved.plan().category()
                    ))
                    .append('\n');
        }
        return result.toString();
    }

    static String prefixSections(String content, String category) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        if (category == null || category.isBlank()) {
            return normalized.endsWith("\n") ? normalized : normalized + '\n';
        }

        StringBuilder migrated = new StringBuilder(normalized.length() + category.length() * 8 + 32);
        migrated.append('[').append(category).append("]\n");
        for (String line : normalized.split("\n", -1)) {
            String leading = line.stripLeading();
            int leadingLength = line.length() - leading.length();
            migrated.append(line, 0, leadingLength)
                    .append(prefixHeader(leading, category))
                    .append('\n');
        }
        return migrated.toString();
    }

    private static String prefixHeader(String line, String category) {
        if (line.startsWith("[[") && line.endsWith("]]")) {
            String path = line.substring(2, line.length() - 2).trim();
            return "[[" + category + "." + path + "]]";
        }
        if (!line.startsWith("[") || !line.endsWith("]")) {
            return line;
        }
        String path = line.substring(1, line.length() - 1).trim();
        return "[" + category + "." + path + "]";
    }

    private static SourcePlan source(String fileName) {
        return source(fileName, null);
    }

    private static SourcePlan source(String fileName, String category) {
        return new SourcePlan(fileName, category);
    }

    private static void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination);
        }
    }

    /** Outcome of preparing all three unified destinations. */
    public enum MigrationResult {
        MIGRATED,
        ALREADY_PRESENT,
        NO_LEGACY_FILES,
        INVALID_DIRECTORY,
        FAILED
    }

    private enum FileResult {
        MIGRATED,
        ALREADY_PRESENT,
        NO_LEGACY_FILES,
        INVALID_DIRECTORY,
        FAILED
    }

    private record FilePlan(String destination, List<SourcePlan> sources) {
    }

    private record SourcePlan(String fileName, String category) {
    }

    private record ResolvedSource(Path path, SourcePlan plan) {
    }
}
