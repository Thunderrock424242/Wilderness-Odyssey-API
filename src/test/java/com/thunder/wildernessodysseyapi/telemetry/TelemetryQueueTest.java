package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryQueueTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void burstEnqueueCoalescesToOneAtomicSnapshot() throws Exception {
        Path spool = temporaryDirectory.resolve("telemetry-queue.jsonl");
        List<Runnable> scheduledWrites = new ArrayList<>();
        TelemetryQueue queue = new TelemetryQueue(spool, task -> {
            scheduledWrites.add(task);
            return true;
        });

        queue.enqueue(payload("one"), 10);
        queue.enqueue(payload("two"), 10);
        queue.enqueue(payload("three"), 10);

        assertEquals(1, scheduledWrites.size());
        scheduledWrites.getFirst().run();
        assertEquals(3, Files.readAllLines(spool).size());
        assertFalse(Files.exists(temporaryDirectory.resolve("telemetry-queue.jsonl.tmp")));

        TelemetryQueue reloaded = new TelemetryQueue(spool, task -> true);
        assertEquals(3, reloaded.stats().pending());
    }

    @Test
    void rejectedPersistenceRemainsRetryableWithoutInlineIo() {
        Path spool = temporaryDirectory.resolve("telemetry-queue.jsonl");
        int[] schedulingAttempts = {0};
        TelemetryQueue queue = new TelemetryQueue(spool, task -> {
            schedulingAttempts[0]++;
            return false;
        });

        queue.enqueue(payload("one"), 10);
        queue.enqueue(payload("two"), 10);

        assertEquals(2, schedulingAttempts[0]);
        assertFalse(Files.exists(spool));
        assertEquals(2, queue.stats().pending());
    }

    private static TelemetryQueue.PendingTelemetryPayload payload(String id) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        return new TelemetryQueue.PendingTelemetryPayload(
                "test",
                body,
                "https://example.invalid/telemetry",
                1,
                0,
                Duration.ZERO,
                Duration.ZERO
        );
    }
}
