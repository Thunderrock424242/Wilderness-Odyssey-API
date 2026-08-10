package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Promotes a verified sibling temporary file while preserving the previous destination on failure.
 *
 * <p>Callers remain responsible for choosing an allowed directory and validating its ancestors.
 * This final boundary rejects symbolic-link destinations and provides a rollback backup on file
 * systems that do not support an atomic replacement.</p>
 */
public final class SafeFilePublisher {

    private SafeFilePublisher() {
    }

    /** Publishes a regular sibling temporary file with atomic replace or backup/rollback fallback. */
    public static void publish(Path temporary, Path destination) throws IOException {
        Path normalizedTemporary = temporary.toAbsolutePath().normalize();
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        if (!normalizedTemporary.getParent().equals(normalizedDestination.getParent())) {
            throw new IOException("Safe publication requires sibling temporary and destination files: "
                    + normalizedTemporary + " -> " + normalizedDestination);
        }
        if (!Files.isRegularFile(normalizedTemporary, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalizedTemporary)) {
            throw new IOException("Safe publication temporary is not a regular non-link file: "
                    + normalizedTemporary);
        }
        rejectUnsafeDestination(normalizedDestination);

        try {
            Files.move(
                    normalizedTemporary,
                    normalizedDestination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            publishWithRollback(normalizedTemporary, normalizedDestination);
        }
    }

    private static void publishWithRollback(Path temporary, Path destination) throws IOException {
        Path backup = null;
        boolean published = false;
        boolean destinationExists = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        if (destinationExists) {
            rejectUnsafeDestination(destination);
            backup = Files.createTempFile(destination.getParent(), ".structuregen-backup-", ".tmp");
            try {
                Files.copy(
                        destination,
                        backup,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
            } catch (IOException backupFailure) {
                Files.deleteIfExists(backup);
                throw backupFailure;
            }
        }

        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            published = true;
        } catch (IOException publicationFailure) {
            if (backup != null) {
                try {
                    Files.copy(
                            backup,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES
                    );
                } catch (IOException rollbackFailure) {
                    publicationFailure.addSuppressed(rollbackFailure);
                    throw new IOException("Safe replacement failed and rollback could not restore the destination; "
                            + "the recovery backup remains at " + backup, publicationFailure);
                }
                deleteBackupBestEffort(backup, "after restoring the previous destination");
                backup = null;
            }
            throw publicationFailure;
        } finally {
            if (published && backup != null) {
                // Publication is already committed. A cleanup-only failure must
                // not be misreported as though the destination were unchanged.
                deleteBackupBestEffort(backup, "after publishing the verified replacement");
            }
        }
    }

    private static void deleteBackupBestEffort(Path backup, String context) {
        try {
            Files.deleteIfExists(backup);
        } catch (IOException cleanupFailure) {
            backup.toFile().deleteOnExit();
            System.err.println("[StructureGen] WARNING Could not delete rollback backup " + backup + " " + context
                    + "; the verified destination is intact and cleanup will be retried on JVM exit: "
                    + cleanupFailure.getMessage());
        }
    }

    private static void rejectUnsafeDestination(Path destination) throws IOException {
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing symbolic-link publication destination: " + destination);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Publication destination is not a regular file: " + destination);
        }
    }
}
