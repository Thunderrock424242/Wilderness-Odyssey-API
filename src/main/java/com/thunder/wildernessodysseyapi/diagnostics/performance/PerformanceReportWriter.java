package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One file-only job and one coalesced terminal snapshot; no owned thread or blocking IO handoff. */
public final class PerformanceReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private final Path directory;
    private final Executor executor;
    private final Consumer<Status> completed;
    // Admission and handoff use this object's monitor; disk IO never holds it.
    private boolean pending;
    private PerformanceReport terminalReport;
    private volatile Status status = new Status(null, false, "No report requested");

    public PerformanceReportWriter(Path directory, Executor executor, Consumer<Status> completed) {
        this.directory = directory;
        this.executor = executor;
        this.completed = completed;
    }

    public synchronized boolean submit(PerformanceReport report) {
        if (pending) return false;
        return dispatch(report);
    }

    /** Keeps at most the newest terminal snapshot while a prior export finishes, without waiting for IO. */
    public synchronized boolean submitFinal(PerformanceReport report) {
        if (pending) {
            terminalReport = report;
            return true;
        }
        return dispatch(report);
    }

    private boolean dispatch(PerformanceReport report) {
        pending = true;
        Path target = directory.resolve("wo-perf-%013d-%06d.json".formatted(System.currentTimeMillis(), SEQUENCE.incrementAndGet()));
        status = new Status(target, true, "Queued");
        try {
            executor.execute(() -> write(target, report));
            return true;
        } catch (RuntimeException rejected) {
            status = new Status(target, false, "Rejected: " + rejected.getClass().getSimpleName());
            pending = false;
            return false;
        }
    }

    public Status status() { return status; }

    private void write(Path target, PerformanceReport report) {
        Status finished = new Status(target, false, "Failed");
        try {
            Files.createDirectories(directory);
            Files.writeString(target, GSON.toJson(report), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            rotate();
            finished = new Status(target, false, "Written");
        } catch (IOException | RuntimeException failure) {
            finished = new Status(target, false, "Failed: " + failure.getClass().getSimpleName());
        } finally {
            complete(finished);
        }
    }

    private void complete(Status finished) {
        Status rejected = null;
        synchronized (this) {
            status = finished;
            pending = false;
            PerformanceReport next = terminalReport;
            terminalReport = null;
            if (next != null && !dispatch(next)) rejected = status;
        }
        // A new report may be admitted now. Publish this job's result rather
        // than accidentally logging the newer job's volatile status.
        completed.accept(finished);
        if (rejected != null) completed.accept(rejected);
    }

    private void rotate() throws IOException {
        try (var files = Files.list(directory)) {
            var reports = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("wo-perf-[0-9]+-[0-9]+\\.json"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
            for (int index = 10; index < reports.size(); index++) Files.deleteIfExists(reports.get(index));
        }
    }

    public record Status(Path path, boolean pending, String result) { }
}
