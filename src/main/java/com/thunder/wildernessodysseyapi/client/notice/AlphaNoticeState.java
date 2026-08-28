package com.thunder.wildernessodysseyapi.client.notice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Reads and writes the installation-local alpha notice acknowledgment.
 *
 * <p>The file records the accepted notice revision, world migration schema,
 * and packaged modpack release. A missing file is a normal first launch, while
 * malformed or unreadable data is reported as an unusable result so the
 * manager can fail open to the title screen.</p>
 */
public final class AlphaNoticeState {
    static final String ACKNOWLEDGED_VERSION_KEY = "acknowledgedNoticeVersion";
    static final String ACKNOWLEDGED_WORLD_SCHEMA_KEY = "acknowledgedWorldSchemaVersion";
    static final String ACKNOWLEDGED_MODPACK_VERSION_KEY = "acknowledgedModpackVersion";

    private AlphaNoticeState() {
    }

    /**
     * Loads the accepted notice version without allowing file errors to escape.
     *
     * @param stateFile installation-local state file
     * @return a readable result, or an unusable result describing why the caller must fail open
     */
    public static ReadResult read(Path stateFile) {
        try {
            if (Files.notExists(stateFile)) {
                return ReadResult.readable(VersionStamp.unacknowledged());
            }

            Properties properties = new Properties();
            try (BufferedReader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }

            String rawVersion = properties.getProperty(ACKNOWLEDGED_VERSION_KEY);
            if (rawVersion == null || rawVersion.isBlank()) {
                return ReadResult.unusable("missing " + ACKNOWLEDGED_VERSION_KEY);
            }

            int acknowledgedVersion = Integer.parseInt(rawVersion.trim());
            if (acknowledgedVersion < 0) {
                return ReadResult.unusable(ACKNOWLEDGED_VERSION_KEY + " cannot be negative");
            }

            int acknowledgedWorldSchema = parseOptionalNonNegativeInt(
                    properties,
                    ACKNOWLEDGED_WORLD_SCHEMA_KEY
            );
            String acknowledgedModpackVersion = properties
                    .getProperty(ACKNOWLEDGED_MODPACK_VERSION_KEY, "")
                    .trim();
            return ReadResult.readable(new VersionStamp(
                    acknowledgedVersion,
                    acknowledgedWorldSchema,
                    acknowledgedModpackVersion
            ));
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage();
            return ReadResult.unusable(
                    exception.getClass().getSimpleName() + (message == null ? "" : ": " + message)
            );
        }
    }

    /**
     * Atomically replaces the accepted version fingerprint when the platform supports it.
     *
     * @param stateFile installation-local state file
     * @param acknowledgedVersions notice, world-schema, and modpack versions the player accepted
     * @throws IOException when the state cannot be persisted
     */
    public static void write(Path stateFile, VersionStamp acknowledgedVersions) throws IOException {
        Path absoluteStateFile = stateFile.toAbsolutePath();
        Path parent = absoluteStateFile.getParent();
        if (parent == null) {
            throw new IOException("Alpha notice state path has no parent: " + stateFile);
        }
        Files.createDirectories(parent);

        Properties properties = new Properties();
        properties.setProperty(
                ACKNOWLEDGED_VERSION_KEY,
                Integer.toString(acknowledgedVersions.noticeVersion())
        );
        properties.setProperty(
                ACKNOWLEDGED_WORLD_SCHEMA_KEY,
                Integer.toString(acknowledgedVersions.worldSchemaVersion())
        );
        properties.setProperty(
                ACKNOWLEDGED_MODPACK_VERSION_KEY,
                acknowledgedVersions.modpackVersion()
        );
        Path temporaryFile = Files.createTempFile(parent, "alpha_notice_", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "Wilderness Odyssey alpha development notice state");
            }
            try {
                Files.move(
                        temporaryFile,
                        absoluteStateFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteStateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static int parseOptionalNonNegativeInt(Properties properties, String key) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null || rawValue.isBlank()) {
            return 0;
        }
        int value = Integer.parseInt(rawValue.trim());
        if (value < 0) {
            throw new IllegalArgumentException(key + " cannot be negative");
        }
        return value;
    }

    /** Version fingerprint accepted by the player for this local installation. */
    public record VersionStamp(int noticeVersion, int worldSchemaVersion, String modpackVersion) {
        public VersionStamp {
            if (noticeVersion < 0) {
                throw new IllegalArgumentException("noticeVersion cannot be negative");
            }
            if (worldSchemaVersion < 0) {
                throw new IllegalArgumentException("worldSchemaVersion cannot be negative");
            }
            modpackVersion = modpackVersion == null ? "" : modpackVersion.trim();
        }

        private static VersionStamp unacknowledged() {
            return new VersionStamp(0, 0, "");
        }
    }

    /** Result of reading an acknowledgment file. */
    public record ReadResult(VersionStamp acknowledgedVersions, boolean readable, String warning) {
        private static ReadResult readable(VersionStamp acknowledgedVersions) {
            return new ReadResult(acknowledgedVersions, true, "");
        }

        private static ReadResult unusable(String warning) {
            return new ReadResult(VersionStamp.unacknowledged(), false, warning);
        }

        /**
         * Returns whether any accepted version differs from the current fingerprint.
         * Unusable state always returns false so startup fails open.
         */
        public boolean requiresNotice(VersionStamp currentVersions) {
            return readable && (
                    acknowledgedVersions.noticeVersion() < currentVersions.noticeVersion()
                            || acknowledgedVersions.worldSchemaVersion() != currentVersions.worldSchemaVersion()
                            || !acknowledgedVersions.modpackVersion().equals(currentVersions.modpackVersion())
            );
        }
    }
}
