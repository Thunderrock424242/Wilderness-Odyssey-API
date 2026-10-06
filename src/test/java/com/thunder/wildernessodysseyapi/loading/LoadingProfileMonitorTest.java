package com.thunder.wildernessodysseyapi.loading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingProfileMonitorTest {
    @TempDir
    Path directory;
    private final List<LoadingProfileMonitor> monitors = new ArrayList<>();

    /** The production close is deliberately asynchronous; TempDir cleanup must wait for its writer. */
    @AfterEach
    void stopWorkersBeforeTemporaryDirectoryCleanup() throws InterruptedException {
        monitors.forEach(LoadingProfileMonitor::close);
        await(() -> Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.isAlive() && thread.getName().equals("WO-Loading-Profiler")));
    }

    @Test
    void savesAnOngoingLoadWithoutAnyFurtherClientHeartbeats() throws Exception {
        try (var monitor = monitor()) {
            monitor.observe("Generating spawn", 25, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
            await(() -> monitor.status().active() && monitor.status().report() != null);

            var status = monitor.status();
            assertTrue(status.snapshot().clientSilenceNanos() >= TimeUnit.SECONDS.toNanos(60));
            String report = Files.readString(status.report());
            assertTrue(report.contains("Loading still in progress"));
            assertTrue(report.contains("25%"));
            assertTrue(report.contains("[Latest Thread Stacks"));

            monitor.stop("World opened");
            await(() -> !monitor.status().active());
            assertTrue(Files.readString(monitor.status().report()).contains("Outcome: World opened"));
        }
    }

    @Test
    void stopsSamplingAfterCancellationAndSeparatesTheNextLoadingOperation() throws Exception {
        try (var monitor = monitor()) {
            monitor.observe("Generating spawn", 80, System.nanoTime() - TimeUnit.SECONDS.toNanos(11));
            await(() -> monitor.status().active());
            monitor.stop("Cancelled");
            await(() -> !monitor.status().active());
            Path firstReport = monitor.status().report();
            long samples = monitor.status().snapshot().samples();
            Thread.sleep(1100);
            assertEquals(samples, monitor.status().snapshot().samples());
            assertTrue(Files.readString(firstReport).contains("Outcome: Cancelled"));

            monitor.observe("Receiving terrain", -1, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
            await(() -> monitor.status().active() && monitor.status().report() != null);
            assertNotEquals(firstReport, monitor.status().report());
            String secondReport = Files.readString(monitor.status().report());
            assertTrue(secondReport.contains("Stage: Receiving terrain"));
            assertTrue(secondReport.contains("Progress: unavailable"));
            assertFalse(secondReport.contains("80%"));
        }
    }

    @Test
    void shutdownFlushesTheCurrentReportAndTerminatesTheDiagnosticWorker() throws Exception {
        var monitor = monitor();
        try {
            monitor.observe("Generating spawn", 5, System.nanoTime() - TimeUnit.SECONDS.toNanos(11));
            await(() -> monitor.status().active());
            monitor.close();
            await(() -> !monitor.status().active() && monitor.status().report() != null);
            assertTrue(Files.readString(monitor.status().report()).contains("Outcome: Client shutting down"));
            await(() -> Thread.getAllStackTraces().keySet().stream()
                    .noneMatch(thread -> thread.isAlive() && thread.getName().equals("WO-Loading-Profiler")));
        } finally {
            monitor.close();
        }
    }

    @Test
    void retainsTenNewReportsWithoutDeletingLegacyStallSnapshots() throws Exception {
        Path legacy = directory.resolve("loading-stall-20000101-000000.log");
        Files.writeString(legacy, "legacy snapshot");
        for (int index = 0; index < 12; index++) {
            Files.writeString(directory.resolve(String.format("loading-profile-20000101-000000-%03d.log", index)), "old report");
        }
        try (var monitor = monitor()) {
            monitor.observe("Generating spawn", 15, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
            await(() -> monitor.status().report() != null);
            try (var reports = Files.list(directory)) {
                assertEquals(10, reports.filter(path -> path.getFileName().toString().startsWith("loading-profile-")).count());
            }
            assertEquals("legacy snapshot", Files.readString(legacy));
            assertTrue(Files.exists(monitor.status().report()));
        }
    }

    @Test
    void reportWriteFailureDoesNotStopSamplingAndRemainsVisibleBetweenRetries() throws Exception {
        Path blockedDirectory = directory.resolve("not-a-directory");
        Files.writeString(blockedDirectory, "ordinary file");
        var monitor = new LoadingProfileMonitor(blockedDirectory, new LoadingModAttribution(Map.of()), List.of(), 60_000);
        monitors.add(monitor);
        try (monitor) {
            monitor.observe("Generating spawn", 50, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
            await(() -> monitor.status().saveFailed());
            long firstSamples = monitor.status().snapshot().samples();
            await(() -> monitor.status().snapshot().samples() > firstSamples);
            assertTrue(monitor.status().active());
            assertTrue(monitor.status().saveFailed());
            assertEquals("ordinary file", Files.readString(blockedDirectory));
        }
    }

    private LoadingProfileMonitor monitor() {
        var monitor = new LoadingProfileMonitor(directory, new LoadingModAttribution(Map.of()), List.of("example@1.0"), 60_000);
        monitors.add(monitor);
        return monitor;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Diagnostic worker did not reach the expected state within 8 seconds");
    }
}
