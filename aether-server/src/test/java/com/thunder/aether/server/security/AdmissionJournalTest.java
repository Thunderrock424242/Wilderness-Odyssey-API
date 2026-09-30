package com.thunder.aether.server.security;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdmissionJournalTest {
    @TempDir Path directory;
    private final Instant now = Instant.parse("2026-09-28T12:00:00Z");

    @Test void pauseSurvivesRestartAndExactReplayDoesNotRepeatMutation() throws Exception {
        JsonObject command = command(0, true);
        try (var journal = new AdmissionJournal(directory)) {
            assertTrue(journal.allowsInference());
            assertEquals(1L, journal.apply(command, now).get("revision"));
            assertFalse(journal.allowsInference());
        }
        try (var journal = new AdmissionJournal(directory)) {
            assertFalse(journal.allowsInference());
            assertEquals(true, journal.apply(command, now).get("replayed"));
            assertEquals(1L, journal.snapshot().get("revision"));
            journal.apply(command(1, false), now);
            assertTrue(journal.allowsInference());
        }
        String audit = Files.readString(directory.resolve("admission.jsonl"));
        assertEquals(2, audit.lines().count());
        assertTrue(audit.contains("kinetic-administration"));
        assertTrue(audit.contains("staff-123"));
    }

    @Test void rejectsStaleRevisionConflictingReplayExpiryAndUnexpectedOperations() throws Exception {
        try (var journal = new AdmissionJournal(directory)) {
            JsonObject first = command(0, true);
            journal.apply(first, now);
            assertThrows(AdmissionJournal.Conflict.class, () -> journal.apply(command(0,false),now));
            first.addProperty("paused", false);
            assertThrows(AdmissionJournal.Conflict.class, () -> journal.apply(first,now));
            JsonObject expired = command(1,false);
            assertThrows(IllegalArgumentException.class, () -> journal.apply(expired,now.plusSeconds(60)));
            expired.addProperty("model", "attacker-model");
            assertThrows(IllegalArgumentException.class, () -> journal.apply(expired,now));
            assertFalse(journal.allowsInference());
            assertEquals(1L, journal.snapshot().get("revision"));
        }
    }

    @Test void corruptOrTornJournalAndDuplicateOwnerFailClosed() throws Exception {
        try (var journal = new AdmissionJournal(directory)) {
            assertThrows(java.io.IOException.class, () -> new AdmissionJournal(directory));
            journal.apply(command(0,true),now);
        }
        Files.writeString(directory.resolve("admission.jsonl"), "{", StandardOpenOption.APPEND);
        assertThrows(java.io.IOException.class, () -> new AdmissionJournal(directory));
    }

    private JsonObject command(long revision, boolean paused) {
        JsonObject value = new JsonObject();
        value.addProperty("requestId", UUID.randomUUID().toString());
        value.addProperty("expectedRevision", revision);
        value.addProperty("expiresAt", now.plusSeconds(60).toString());
        value.addProperty("paused", paused);
        value.addProperty("actor", "staff-123");
        return value;
    }
}
