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
 * <p>The file contains only the last accepted notice version. A missing file is
 * a normal first launch, while malformed or unreadable data is reported as an
 * unusable result so the manager can fail open to the title screen.</p>
 */
public final class AlphaNoticeState {
    static final String ACKNOWLEDGED_VERSION_KEY = "acknowledgedNoticeVersion";

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
                return ReadResult.readable(0);
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
            return ReadResult.readable(acknowledgedVersion);
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage();
            return ReadResult.unusable(
                    exception.getClass().getSimpleName() + (message == null ? "" : ": " + message)
            );
        }
    }

    /**
     * Atomically replaces the accepted notice version when the platform supports it.
     *
     * @param stateFile installation-local state file
     * @param acknowledgedVersion notice version the player accepted
     * @throws IOException when the state cannot be persisted
     */
    public static void write(Path stateFile, int acknowledgedVersion) throws IOException {
        if (acknowledgedVersion < 0) {
            throw new IllegalArgumentException("acknowledgedVersion cannot be negative");
        }

        Path absoluteStateFile = stateFile.toAbsolutePath();
        Path parent = absoluteStateFile.getParent();
        if (parent == null) {
            throw new IOException("Alpha notice state path has no parent: " + stateFile);
        }
        Files.createDirectories(parent);

        Properties properties = new Properties();
        properties.setProperty(ACKNOWLEDGED_VERSION_KEY, Integer.toString(acknowledgedVersion));
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

    /** Result of reading an acknowledgment file. */
    public record ReadResult(int acknowledgedVersion, boolean readable, String warning) {
        private static ReadResult readable(int acknowledgedVersion) {
            return new ReadResult(acknowledgedVersion, true, "");
        }

        private static ReadResult unusable(String warning) {
            return new ReadResult(0, false, warning);
        }

        /**
         * Returns whether this valid state predates the supplied notice version.
         * Unusable state always returns false so startup fails open.
         */
        public boolean requiresNotice(int currentNoticeVersion) {
            return readable && acknowledgedVersion < currentNoticeVersion;
        }
    }
}
