package com.thunder.wildernessodysseyapi.client.notice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies first-launch, version-bump, persistence, and fail-open state behavior. */
class AlphaNoticeStateTest {
    private static final AlphaNoticeState.VersionStamp CURRENT_VERSIONS =
            new AlphaNoticeState.VersionStamp(1, 3, "4.2.0");

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingStateRequiresTheCurrentNotice() {
        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(temporaryDirectory.resolve("missing.properties"));

        assertTrue(result.readable());
        assertEquals(0, result.acknowledgedVersions().noticeVersion());
        assertTrue(result.requiresNotice(CURRENT_VERSIONS));
    }

    @Test
    void acceptedVersionsPersistAndAnyVersionChangeShowsAgain() throws IOException {
        Path stateFile = temporaryDirectory.resolve("alpha_notice.properties");
        AlphaNoticeState.write(stateFile, CURRENT_VERSIONS);

        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(stateFile);
        assertTrue(result.readable());
        assertEquals(CURRENT_VERSIONS, result.acknowledgedVersions());
        assertFalse(result.requiresNotice(CURRENT_VERSIONS));
        assertTrue(result.requiresNotice(new AlphaNoticeState.VersionStamp(2, 3, "4.2.0")));
        assertTrue(result.requiresNotice(new AlphaNoticeState.VersionStamp(1, 4, "4.2.0")));
        assertTrue(result.requiresNotice(new AlphaNoticeState.VersionStamp(1, 3, "4.2.1")));
    }

    @Test
    void legacyNoticeOnlyStateShowsOnceForTheVersionFingerprintUpgrade() throws IOException {
        Path stateFile = temporaryDirectory.resolve("alpha_notice.properties");
        Files.writeString(stateFile, AlphaNoticeState.ACKNOWLEDGED_VERSION_KEY + "=1");

        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(stateFile);
        assertTrue(result.readable());
        assertEquals(1, result.acknowledgedVersions().noticeVersion());
        assertEquals(0, result.acknowledgedVersions().worldSchemaVersion());
        assertTrue(result.acknowledgedVersions().modpackVersion().isBlank());
        assertTrue(result.requiresNotice(CURRENT_VERSIONS));
    }

    @Test
    void malformedStateFailsOpenInsteadOfRequestingTheNotice() throws IOException {
        Path stateFile = temporaryDirectory.resolve("alpha_notice.properties");
        Files.writeString(stateFile, AlphaNoticeState.ACKNOWLEDGED_VERSION_KEY + "=not-a-number");

        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(stateFile);
        assertFalse(result.readable());
        assertFalse(result.requiresNotice(CURRENT_VERSIONS));
        assertFalse(result.warning().isBlank());
    }

    @Test
    void unreadableStatePathFailsOpenInsteadOfBlockingStartup() {
        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(temporaryDirectory);

        assertFalse(result.readable());
        assertFalse(result.requiresNotice(CURRENT_VERSIONS));
        assertFalse(result.warning().isBlank());
    }
}
