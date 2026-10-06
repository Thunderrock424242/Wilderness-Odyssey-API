package com.thunder.wildernessodysseyapi.loading;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Client-lifetime diagnostic worker. Only immutable screen observations cross
 * the thread boundary; the worker never accesses a Minecraft world or screen.
 * A single fixed-delay task samples active loads and writes reports even if
 * the client thread stops rendering. Idle clients do not collect thread stacks.
 */
public final class LoadingProfileMonitor implements AutoCloseable {
    private static final long REPORT_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final long FINAL_REPORT_MINIMUM_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final int MAX_CAPTURED_THREADS = 128;
    private static final int MAX_REPORTS = 10;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());
    private final Path reportDirectory;
    private final Function<StackTraceElement, String> ownerResolver;
    private final List<String> loadedMods;
    private final long reportDelayNanos;
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final AtomicReference<Observation> observation = new AtomicReference<>();
    private final ScheduledThreadPoolExecutor worker;
    private volatile Status status = new Status(false, null, null, false);
    private long nextId;
    private boolean closed;
    // The following fields belong exclusively to the diagnostic worker.
    private Observation active;
    private LoadingProfileSession session;
    private Path reportFile;
    private Path lastSavedReport;
    private long lastReportNanos;
    private boolean samplingFailureLogged;
    private boolean lastSaveFailed;

    public LoadingProfileMonitor(Path reportDirectory, Function<StackTraceElement, String> ownerResolver,
                                 List<String> loadedMods, long reportDelayMillis) {
        this.reportDirectory = reportDirectory;
        this.ownerResolver = ownerResolver;
        this.loadedMods = List.copyOf(loadedMods);
        this.reportDelayNanos = TimeUnit.MILLISECONDS.toNanos(reportDelayMillis);
        worker = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "WO-Loading-Profiler");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
        worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        // Queue use is bounded by construction: one periodic task and at most
        // one shutdown flush. Arbitrary external tasks cannot be submitted.
        worker.scheduleWithFixedDelay(this::pollSafely, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void observe(String phase, int progress, long nowNanos) {
        if (closed) {
            return;
        }
        Observation previous = observation.get();
        boolean newOperation = previous == null || previous.phase() == null;
        long id = newOperation ? ++nextId : previous.id();
        long started = newOperation ? nowNanos : previous.startedNanos();
        Instant timestamp = newOperation ? Instant.now() : previous.timestamp();
        observation.set(new Observation(id, started, nowNanos, timestamp, phase, progress, "Loading"));
    }

    public synchronized void stop(String outcome) {
        Observation previous = observation.get();
        if (previous != null && previous.phase() != null) {
            observation.set(new Observation(previous.id(), previous.startedNanos(), System.nanoTime(),
                    previous.timestamp(), null, -1, outcome));
        }
    }

    public Status status() {
        return status;
    }

    /** Stops sampling and queues one final flush without waiting on the game thread. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        stop("Client shutting down");
        worker.execute(this::pollSafely);
        worker.shutdown();
    }

    private void pollSafely() {
        if (samplingFailureLogged) {
            return;
        }
        try {
            poll();
        } catch (RuntimeException | LinkageError exception) {
            if (!samplingFailureLogged) {
                ModConstants.LOGGER.warn("[Loading profiler] JVM sampling unavailable; loading continues normally.", exception);
                samplingFailureLogged = true;
            }
            // Do not let a failed diagnostic task retain an active session or
            // terminate the scheduler's future polling silently.
            session = null;
            active = null;
            status = new Status(false, null, lastSavedReport, true);
        }
    }

    private void poll() {
        Observation latest = observation.get();
        long now = System.nanoTime();
        if (session != null && (latest == null || latest.phase() == null || latest.id() != active.id())) {
            long finished = latest != null && latest.id() == active.id() ? latest.observedNanos() : now;
            String outcome = latest != null && latest.id() == active.id() ? latest.outcome() : "Another loading operation started";
            finish(finished, outcome);
        }
        if (latest == null || latest.phase() == null) {
            return;
        }
        if (session == null) {
            active = latest;
            session = new LoadingProfileSession(latest.startedNanos(), ownerResolver);
            reportFile = reportDirectory.resolve("loading-profile-" + FILE_TIME.format(latest.timestamp()) + "-" + latest.id() + ".log");
            lastReportNanos = latest.startedNanos();
            lastSaveFailed = false;
            ModConstants.LOGGER.info("[Loading profiler] Started automatic loading diagnostics.");
        }
        session.observe(latest.phase(), latest.progress(), latest.observedNanos());
        session.sample(captureThreads());
        var snapshot = session.snapshot(now);
        if (snapshot.elapsedNanos() >= reportDelayNanos && now - lastReportNanos >= REPORT_INTERVAL_NANOS) {
            lastReportNanos = now;
            lastSaveFailed = !save(now, "Loading still in progress");
        }
        status = new Status(true, snapshot, reportFile.equals(lastSavedReport) ? lastSavedReport : null, lastSaveFailed);
    }

    private List<LoadingProfileSession.ThreadSample> captureThreads() {
        // Enumerate lightweight metadata first, then capture at most 128 stacks.
        // Main/server threads and world-generation worker pools get priority.
        ThreadInfo[] metadata = threadBean.getThreadInfo(threadBean.getAllThreadIds(), 0);
        long[] ids = Arrays.stream(metadata).filter(info -> info != null && !info.getThreadName().startsWith("WO-Loading-Profiler"))
                .sorted(Comparator.comparingInt(LoadingProfileMonitor::threadPriority).reversed()
                        .thenComparing(ThreadInfo::getThreadName))
                .limit(MAX_CAPTURED_THREADS).mapToLong(ThreadInfo::getThreadId).toArray();
        ThreadInfo[] captured = threadBean.getThreadInfo(ids, 48);
        boolean cpuAvailable = threadBean.isThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled();
        List<LoadingProfileSession.ThreadSample> samples = new ArrayList<>(captured.length);
        for (ThreadInfo info : captured) {
            if (info != null) {
                samples.add(new LoadingProfileSession.ThreadSample(info.getThreadId(), info.getThreadName(),
                        info.getThreadState(), List.of(info.getStackTrace()),
                        cpuAvailable ? threadBean.getThreadCpuTime(info.getThreadId()) : -1));
            }
        }
        return samples;
    }

    private static int threadPriority(ThreadInfo info) {
        String name = info.getThreadName();
        if (name.equals("Render thread") || name.equals("Server thread")) {
            return 4;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("worker") || lower.contains("worldgen") || lower.contains("chunk") || lower.contains("forkjoin")) {
            return 3;
        }
        return info.getThreadState() == Thread.State.RUNNABLE || info.getThreadState() == Thread.State.BLOCKED ? 2 : 1;
    }

    private void finish(long now, String outcome) {
        var snapshot = session.snapshot(now);
        boolean saveFailed = false;
        if (snapshot.elapsedNanos() >= FINAL_REPORT_MINIMUM_NANOS || reportFile.equals(lastSavedReport)) {
            saveFailed = !save(now, outcome);
        }
        status = new Status(false, snapshot, lastSavedReport, saveFailed);
        session = null;
        active = null;
    }

    private boolean save(long now, String outcome) {
        Path temporary = reportFile.resolveSibling(reportFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(reportDirectory);
            Files.writeString(temporary, "Started: " + active.timestamp() + "\n" + session.report(now, outcome, loadedMods));
            try {
                Files.move(temporary, reportFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, reportFile, StandardCopyOption.REPLACE_EXISTING);
            }
            lastSavedReport = reportFile;
            ModConstants.LOGGER.info("[Loading profiler] Saved loading diagnostics to {}", reportFile.toAbsolutePath());
            trimReports();
            return true;
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[Loading profiler] Could not save diagnostics to {}", reportFile, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            return false;
        }
    }

    private void trimReports() throws IOException {
        try (var entries = Files.list(reportDirectory)) {
            List<Path> reports = entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("loading-profile-")
                            && path.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
            for (int index = MAX_REPORTS; index < reports.size(); index++) {
                Files.deleteIfExists(reports.get(index));
            }
        }
    }

    private record Observation(long id, long startedNanos, long observedNanos, Instant timestamp,
                               String phase, int progress, String outcome) {
    }

    public record Status(boolean active, LoadingProfileSession.Snapshot snapshot, Path report, boolean saveFailed) {
    }
}
