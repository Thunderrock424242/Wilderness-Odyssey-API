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
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingStateRequiresTheCurrentNotice() {
        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(temporaryDirectory.resolve("missing.properties"));

        assertTrue(result.readable());
        assertEquals(0, result.acknowledgedVersion());
        assertTrue(result.requiresNotice(1));
    }

    @Test
    void acceptedVersionPersistsAndAFutureVersionShowsAgain() throws IOException {
        Path stateFile = temporaryDirectory.resolve("alpha_notice.properties");
        AlphaNoticeState.write(stateFile, 1);

        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(stateFile);
        assertTrue(result.readable());
        assertEquals(1, result.acknowledgedVersion());
        assertFalse(result.requiresNotice(1));
        assertTrue(result.requiresNotice(2));
    }

    @Test
    void malformedStateFailsOpenInsteadOfRequestingTheNotice() throws IOException {
        Path stateFile = temporaryDirectory.resolve("alpha_notice.properties");
        Files.writeString(stateFile, AlphaNoticeState.ACKNOWLEDGED_VERSION_KEY + "=not-a-number");

        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(stateFile);
        assertFalse(result.readable());
        assertFalse(result.requiresNotice(1));
        assertFalse(result.warning().isBlank());
    }

    @Test
    void unreadableStatePathFailsOpenInsteadOfBlockingStartup() {
        AlphaNoticeState.ReadResult result = AlphaNoticeState.read(temporaryDirectory);

        assertFalse(result.readable());
        assertFalse(result.requiresNotice(1));
        assertFalse(result.warning().isBlank());
    }
}
