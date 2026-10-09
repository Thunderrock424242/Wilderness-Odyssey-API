package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One admitted file-only job on an existing executor; no owned thread, queue or blocking handoff. */
public final class PerformanceReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private final Path directory;
    private final Executor executor;
    private final Consumer<Status> completed;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile Status status = new Status(null, false, "No report requested");

    public PerformanceReportWriter(Path directory, Executor executor, Consumer<Status> completed) {
        this.directory = directory;
        this.executor = executor;
        this.completed = completed;
    }

    public boolean submit(PerformanceReport report) {
        if (!pending.compareAndSet(false, true)) return false;
        Path target = directory.resolve("wo-perf-%013d-%06d.json".formatted(System.currentTimeMillis(), SEQUENCE.incrementAndGet()));
        status = new Status(target, true, "Queued");
        try {
            executor.execute(() -> write(target, report));
            return true;
        } catch (RuntimeException rejected) {
            status = new Status(target, false, "Rejected: " + rejected.getClass().getSimpleName());
            pending.set(false);
            return false;
        }
    }

    public Status status() { return status; }

    private void write(Path target, PerformanceReport report) {
        Status finished;
        try {
            Files.createDirectories(directory);
            Files.writeString(target, GSON.toJson(report), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            rotate();
            finished = new Status(target, false, "Written");
            status = finished;
        } catch (IOException | RuntimeException failure) {
            finished = new Status(target, false, "Failed: " + failure.getClass().getSimpleName());
            status = finished;
        } finally {
            pending.set(false);
        }
        // A new report may be admitted now. Publish this job's result rather
        // than accidentally logging the newer job's volatile status.
        completed.accept(finished);
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
