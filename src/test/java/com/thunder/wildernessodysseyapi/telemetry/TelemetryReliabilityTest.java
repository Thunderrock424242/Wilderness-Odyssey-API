package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.playtest.LocalWebhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryReliabilityTest {
    @TempDir Path directory;

    private TelemetryQueue.PendingTelemetryPayload payload(String endpoint) {
        JsonObject body = new JsonObject();
        body.addProperty("event_type", "test");
        return new TelemetryQueue.PendingTelemetryPayload("event", body, endpoint, 1, 0, Duration.ZERO, Duration.ZERO);
    }

    @Test
    void corruptMiddleRowDoesNotHideLaterReports() throws Exception {
        Path spool = directory.resolve("queue.jsonl");
        var queue = new TelemetryQueue(spool, task -> { task.run(); return true; });
        queue.enqueue(payload("https://example.invalid/"), 10);
        String valid = Files.readString(spool);
        Files.writeString(spool, valid + "not-json\n" + valid);
        var loaded = new TelemetryQueue(spool, task -> true);
        assertEquals(2, loaded.stats().pending());
        assertEquals(1, loaded.stats().dropped());
    }

    @Test
    void boundedReaderSkipsOversizedRowsAndLimitsEntries() throws Exception {
        List<String> accepted = new ArrayList<>();
        assertEquals(2, TelemetryQueue.readBoundedRows(new StringReader(
                "x".repeat(256 * 1024 + 1) + "\none\ntwo\nthree\n"), 2, accepted::add));
        assertEquals(List.of("one", "two"), accepted);
        assertFalse(TelemetryQueue.hasBoundedNesting("[".repeat(65) + "0" + "]".repeat(65)));
        JsonObject object = new JsonObject();
        object.addProperty("message", "[{".repeat(100));
        assertTrue(TelemetryQueue.hasBoundedNesting(object.toString()));
    }

    @Test
    void retriesTheSamePersistedReportAfterTheEndpointRecovers() throws Exception {
        Path spool = directory.resolve("queue.jsonl");
        var queue = new TelemetryQueue(spool, task -> { task.run(); return true; });
        try (var endpoint = new LocalWebhook(503, "", false)) {
            queue.enqueue(payload(endpoint.endpoint()), 2);
            assertEquals(1, queue.flush(2));
            assertEquals(1, queue.stats().pending());
            assertEquals(1, queue.stats().retrying());
            assertNull(queue.stats().lastSuccess());
            String reportId = com.google.gson.JsonParser.parseString(endpoint.request)
                    .getAsJsonObject().get("report_id").getAsString();

            // Model a restart from the actual persisted spool while the endpoint is still unavailable.
            var reloaded = new TelemetryQueue(spool, task -> { task.run(); return true; });
            assertEquals(1, reloaded.stats().pending());
            assertEquals(1, reloaded.stats().retrying());
            endpoint.status = 204;
            assertEquals(1, reloaded.flush(2));
            assertEquals(reportId, com.google.gson.JsonParser.parseString(endpoint.request)
                    .getAsJsonObject().get("report_id").getAsString());
            assertEquals(0, reloaded.stats().pending());
            assertEquals(0, reloaded.stats().retrying());
            assertEquals(0, reloaded.stats().inFlight());
            assertNotNull(reloaded.stats().lastSuccess());
            assertTrue(Files.readString(spool).isEmpty());
        }
    }

    @Test
    void overflowIsBoundedAndVisibleAcrossPersistence() throws Exception {
        Path spool = directory.resolve("queue.jsonl");
        var queue = new TelemetryQueue(spool, task -> { task.run(); return true; });
        for (int i = 0; i < 5; i++) {
            queue.enqueue(payload("https://example.invalid/"), 2);
        }
        assertEquals(2, queue.stats().pending());
        assertEquals(3, queue.stats().dropped());
        assertEquals(2, Files.readAllLines(spool).size());
    }

    @Test
    void interruptedFlushRetainsReportsWithoutStartingNetwork() throws Exception {
        var queue = new TelemetryQueue(directory.resolve("queue.jsonl"), task -> true);
        queue.enqueue(payload("https://example.invalid/"), 2);
        Thread.currentThread().interrupt();
        try {
            assertEquals(0, queue.flush(2));
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, queue.stats().pending());
    }
}