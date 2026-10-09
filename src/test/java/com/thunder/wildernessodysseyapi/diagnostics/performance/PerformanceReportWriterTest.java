package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceReportWriterTest {
    @TempDir Path directory;

    @Test
    void admitsOnlyOneDetachedReportAndWritesOnlyOnTheExecutor() throws Exception {
        var work = new ArrayDeque<Runnable>();
        var writer = new PerformanceReportWriter(directory, work::addLast, ignored -> { });
        assertTrue(writer.submit(report()));
        assertFalse(writer.submit(report()));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        work.removeFirst().run();
        assertFalse(writer.status().pending());
        var json = JsonParser.parseString(Files.readString(writer.status().path())).getAsJsonObject();
        assertEquals("snapshot", json.get("outcome").getAsString());
        assertEquals("unavailable in Phase 0", json.get("saveBacklog").getAsString());
    }

    @Test
    void rejectionNeverWritesInlineAndAllowsASecondSubmission() {
        int[] attempts = {0};
        var writer = new PerformanceReportWriter(directory, task -> {
            if (attempts[0]++ == 0) throw new RejectedExecutionException();
        }, ignored -> { });
        assertFalse(writer.submit(report()));
        assertFalse(writer.status().pending());
        assertTrue(writer.submit(report()));
    }

    @Test
    void fileFailureReleasesAdmissionAndReportsFailure() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "keep");
        var work = new ArrayDeque<Runnable>();
        var completions = new ArrayDeque<PerformanceReportWriter.Status>();
        var writer = new PerformanceReportWriter(blocked, work::addLast, completions::addLast);
        assertTrue(writer.submit(report()));
        work.removeFirst().run();
        assertFalse(writer.status().pending());
        assertTrue(completions.removeFirst().result().startsWith("Failed:"));
        assertEquals("keep", Files.readString(blocked));
        assertTrue(writer.submit(report()));
    }

    @Test
    void rotatesOnlyItsOwnReports() throws Exception {
        Files.writeString(directory.resolve("other.json"), "keep");
        var work = new ArrayDeque<Runnable>();
        var writer = new PerformanceReportWriter(directory, work::addLast, ignored -> { });
        for (int count = 0; count < 12; count++) {
            assertTrue(writer.submit(report()));
            work.removeFirst().run();
        }
        try (var files = Files.list(directory)) {
            assertEquals(10, files.filter(path -> path.getFileName().toString().startsWith("wo-perf-")).count());
        }
        assertEquals("keep", Files.readString(directory.resolve("other.json")));
    }

    private static PerformanceReport report() {
        return new PerformanceReport(1, "test", "now", "snapshot", true, "AUTO", Map.of(), Map.of(),
                new PerformanceObserver(4).snapshot(), new PerformanceTimings(2, 2, 1, 1).snapshot(),
                Map.of(), List.of(), "unavailable in Phase 0", List.of());
    }
}
