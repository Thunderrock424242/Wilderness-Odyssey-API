package com.thunder.wildernessodysseyapi.compat.create;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * Non-destructively recovers schematics written through the historical Create path override.
 *
 * <p>The removed override merged Create's local schematic root and server-upload
 * directory. Root files are therefore restored to Create's local schematic root,
 * while nested {@code player/file.nbt} entries are restored beneath Create's
 * uploaded-schematic root. The migration never copies symbolic-link entries,
 * overwrites a destination, or removes the source. A completion marker prevents
 * repeated directory scans only after every source file is copied or byte-identical
 * to its destination.</p>
 */
public final class LegacyCreateSchematicMigration {

    private static final String MARKER_FILE = ".wilderness_odyssey_legacy_migration_v1";

    private LegacyCreateSchematicMigration() {
    }

    /**
     * Copies legacy schematic files into Create's standard directory without overwriting data.
     *
     * @param legacyDirectory directory used by the removed Wilderness Odyssey path override
     * @param createSchematicDirectory Create's normal local schematic directory
     * @param uploadedSchematicDirectory Create's normal server-upload directory
     * @return counts describing copied, already-present, and conflicting files
     * @throws IOException if a filesystem failure prevents a safe complete pass
     */
    public static MigrationResult migrate(
            Path legacyDirectory,
            Path createSchematicDirectory,
            Path uploadedSchematicDirectory
    ) throws IOException {
        Path sourceRoot = legacyDirectory.toAbsolutePath().normalize();
        Path targetRoot = createSchematicDirectory.toAbsolutePath().normalize();
        Path uploadedTargetRoot = uploadedSchematicDirectory.toAbsolutePath().normalize();

        if (sourceRoot.equals(targetRoot)
                || sourceRoot.equals(uploadedTargetRoot)
                || !Files.isDirectory(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new MigrationResult(0, 0, 0, true);
        }

        Path marker = targetRoot.resolve(MARKER_FILE);
        if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            return new MigrationResult(0, 0, 0, true);
        }

        Files.createDirectories(targetRoot);
        int copied = 0;
        int alreadyPresent = 0;
        int conflicts = 0;

        // Files.walk does not follow symbolic links unless FOLLOW_LINKS is
        // explicitly requested. The NOFOLLOW check also excludes link entries.
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (var iterator = paths.iterator(); iterator.hasNext();) {
                Path source = iterator.next();
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                Path relative = sourceRoot.relativize(source);
                Path destinationRoot = relative.getNameCount() == 1 ? targetRoot : uploadedTargetRoot;
                Path destination = destinationRoot.resolve(relative).normalize();
                if (!destination.startsWith(destinationRoot)) {
                    throw new IOException("Legacy schematic escaped the destination root: " + source);
                }

                Path destinationParent = destination.getParent();
                if (destinationParent == null || !createDirectoryPath(destinationParent)) {
                    conflicts++;
                    continue;
                }

                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (sameRegularFileContents(source, destination)) {
                        alreadyPresent++;
                    } else {
                        conflicts++;
                    }
                    continue;
                }

                try {
                    copyWithoutOverwrite(source, destination);
                    copied++;
                } catch (FileAlreadyExistsException exception) {
                    // Another initializer may have won the race. Treat only a
                    // byte-identical regular file as safely present.
                    if (sameRegularFileContents(source, destination)) {
                        alreadyPresent++;
                    } else {
                        conflicts++;
                    }
                }
            }
        }

        boolean completed = conflicts == 0;
        if (completed) {
            try {
                Files.writeString(
                        marker,
                        "Legacy Wilderness Odyssey Create schematic migration completed.\n",
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
            } catch (FileAlreadyExistsException ignored) {
                // A concurrent safe migration already recorded completion.
            }
        }

        return new MigrationResult(copied, alreadyPresent, conflicts, completed);
    }

    private static boolean createDirectoryPath(Path directory) throws IOException {
        try {
            Files.createDirectories(directory);
            return Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS);
        } catch (FileAlreadyExistsException exception) {
            return false;
        }
    }

    private static boolean sameRegularFileContents(Path source, Path destination) throws IOException {
        return Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                && Files.size(source) == Files.size(destination)
                && Files.mismatch(source, destination) == -1L;
    }

    private static void copyWithoutOverwrite(Path source, Path destination) throws IOException {
        Path stagedCopy = Files.createTempFile(
                destination.getParent(),
                ".wilderness-odyssey-schematic-",
                ".tmp"
        );
        try {
            // Only the private temporary file is replaceable. Publishing uses a
            // move without REPLACE_EXISTING, so an existing user schematic wins.
            Files.copy(source, stagedCopy, StandardCopyOption.REPLACE_EXISTING);
            Files.move(stagedCopy, destination);
        } finally {
            Files.deleteIfExists(stagedCopy);
        }
    }

    /** Summary of one non-destructive legacy schematic migration pass. */
    public record MigrationResult(
            int copiedFiles,
            int alreadyPresentFiles,
            int conflictingFiles,
            boolean completed
    ) {
    }
}
