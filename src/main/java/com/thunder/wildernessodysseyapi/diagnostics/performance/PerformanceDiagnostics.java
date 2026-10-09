package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundEfficiencyManager;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Read-only server diagnostics owner. Tick/JVM/history fields belong to the
 * server thread; cross-thread chunk evidence lives in PerformanceTimings.
 * Workers receive detached reports only. Current server identity is released
 * on stopped; stale callbacks cannot publish into a later integrated world.
 */
public final class PerformanceDiagnostics {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Session current;
    private static volatile PerformanceReport lastReport;
    private static PerformanceReportWriter writer;

    private PerformanceDiagnostics() { }

    public static void start(MinecraftServer server) {
        safely(() -> {
            current = null;
            FAILURE_LOGGED.set(false);
            Map<String, String> mods = new HashMap<>();
            ModList.get().getMods().stream().limit(4096).forEach(mod -> mods.put(mod.getModId(), mod.getVersion().toString()));
            var settings = PerformanceDiagnosticsConfig.values();
            current = new Session(server, settings, mods, environment(settings, mods));
            if (writer == null) {
                writer = new PerformanceReportWriter(FMLPaths.GAMEDIR.get().resolve("logs/wo-performance"),
                        task -> Util.ioPool().execute(task), status -> {
                    if (status.result().equals("Written")) ModConstants.LOGGER.info("[WO Performance] Report written: {}", status.path());
                    else ModConstants.LOGGER.warn("[WO Performance] Diagnostic report {}", status.result());
                });
            }
        });
    }

    public static void milestone(MinecraftServer server, String name) {
        Session session = observed(server);
        if (session != null) safely(() -> session.timings.recordPhase(name, System.nanoTime() - session.startedNanos, true));
    }

    public static void playerLoggedIn(MinecraftServer server) {
        Session session = observed(server);
        if (session != null && !session.playerTickRecorded) session.playerTickPending = true;
    }

    public static void beginTick(MinecraftServer server) {
        Session session = observed(server);
        if (session != null) session.tickStartNanos = System.nanoTime();
    }

    public static void endTick(MinecraftServer server) {
        Session session = observed(server);
        if (session == null) return;
        safely(() -> {
            long now = System.nanoTime();
            if (session.tickStartNanos != 0) session.observer.recordTick(now - session.tickStartNanos);
            session.tickStartNanos = 0;
            if (session.playerTickPending) {
                session.timings.recordPhase("entry/about_to_first_player_tick", now - session.startedNanos, true);
                session.playerTickPending = false;
                session.playerTickRecorded = true;
            }
            if (++session.ticksSinceSample >= session.settings.sampleIntervalTicks()) {
                session.ticksSinceSample = 0;
                if (!session.jvmUnavailable) {
                    try {
                        session.observer.recordJvm(session.jvm.sample(now));
                    } catch (RuntimeException | LinkageError unsupported) {
                        session.jvmUnavailable = true;
                        logFailure(unsupported);
                    }
                }
                if (session.recording) session.addSample(now, queues());
            }
        });
    }

    public static void stopping(MinecraftServer server) {
        Session session = observed(server);
        if (session != null) safely(() -> {
            session.stoppingNanos = System.nanoTime();
            session.shutdownQueues = queues();
        });
    }

    public static void stopped(MinecraftServer server) {
        Session session = current;
        if (session == null || session.server != server) return;
        try {
            safely(() -> {
                if (session.settings.observation() != PerformanceDiagnosticsConfig.Mode.OFF) {
                    if (session.stoppingNanos != 0)
                        session.timings.recordPhase("shutdown/stopping_to_stopped", System.nanoTime() - session.stoppingNanos, true);
                    lastReport = session.report("server stopped", queues());
                    if (session.recording && !writer.submit(lastReport))
                        ModConstants.LOGGER.warn("[WO Performance] Final report retained in memory; writer is busy or unavailable.");
                }
            });
        } finally {
            current = null;
        }
    }

    /** A cold-path wrapper for existing WO lifecycle actions, without any scheduling or cancellation. */
    public static void runPhase(MinecraftServer server, String name, Runnable original) {
        measure(server, name, () -> { original.run(); return null; });
    }

    public static <T> T measure(Object server, String name, Supplier<T> original) {
        Session session = observed(server);
        if (session == null) return original.get();
        return PerformanceMeasurement.call(System::nanoTime, original, (duration, success) -> {
            if (current == session) session.timings.recordPhase(name, duration, success);
        });
    }

    public static <T> T measureChunkRequest(Object server, String dimension, int x, int z, Supplier<T> original) {
        Session session = observed(server);
        if (session == null) return original.get();
        return PerformanceMeasurement.call(System::nanoTime, original, (duration, success) -> {
            if (current != session) return;
            if (duration < session.waitThresholdNanos) return;
            session.timings.recordChunkWait(dimension, x, z, duration, Thread.currentThread().getName(), System.nanoTime(),
                    () -> session.settings.chunkWaitStacks() ? StackWalker.getInstance().walk(frames ->
                            frames.limit(16).map(Object::toString).toList()) : List.of());
        });
    }

    /** Called on the operator/server command thread, never from a worker. */
    public static PerformanceReport report() {
        Session session = current;
        return session == null ? lastReport : session.report("snapshot", queues());
    }

    public static boolean setRecording(boolean enabled) {
        Session session = current;
        return session != null && session.setRecording(enabled);
    }

    public static boolean recording() { return current != null && current.recording; }

    public static boolean export() {
        PerformanceReport report = report();
        return report != null && writer != null && writer.submit(report);
    }

    public static PerformanceReportWriter.Status writerStatus() {
        return writer == null ? new PerformanceReportWriter.Status(null, false, "No report requested") : writer.status();
    }

    private static Session observed(Object server) {
        Session session = current;
        return session != null && session.observes(server) ? session : null;
    }

    private static Map<String, Long> queues() {
        var pools = AsyncTaskManager.workerPools();
        var background = BackgroundEfficiencyManager.async().snapshot();
        var backgroundMetrics = BackgroundEfficiencyManager.metrics().snapshot();
        var data = DataEngine.get().metricsSnapshot();
        var tick = TickEngine.snapshot();
        Map<String, Long> values = new HashMap<>();
        values.put("sharedCpuActive", (long) pools.cpu().activeWorkers());
        values.put("sharedCpuMaximum", (long) pools.cpu().maximumWorkers());
        values.put("sharedCpuQueued", (long) pools.cpu().queuedTasks());
        values.put("sharedIoActive", (long) pools.io().activeWorkers());
        values.put("sharedIoMaximum", (long) pools.io().maximumWorkers());
        values.put("sharedIoQueued", (long) pools.io().queuedTasks());
        values.put("retiredPools", (long) pools.retiredPools());
        values.put("retiredActive", (long) pools.retiredActive());
        values.put("retiredQueued", (long) pools.retiredQueued());
        values.put("sharedMainThreadBacklog", (long) AsyncTaskManager.snapshot().mainThreadBacklog());
        values.put("backgroundCpuActive", (long) background.activeJobs());
        values.put("backgroundCpuQueued", (long) background.workerQueueSize());
        values.put("backgroundApplyQueued", (long) background.applyQueueSize());
        values.put("backgroundScheduled", (long) backgroundMetrics.queuedTasks());
        values.put("tickDeferred", (long) tick.deferredTasks());
        values.put("tickOptionalBudgetNanos", (long) (tick.optionalBudgetMillis() * 1_000_000));
        values.put("dataQueued", data.enabled() ? data.queuedWork() : -1L);
        values.put("dataDirtyEntries", data.enabled() ? data.dirtyEntries() : -1L);
        return Map.copyOf(values);
    }

    private static void safely(Runnable observation) {
        try { observation.run(); } catch (RuntimeException | LinkageError failure) { logFailure(failure); }
    }

    private static void logFailure(Throwable failure) {
        if (FAILURE_LOGGED.compareAndSet(false, true))
            ModConstants.LOGGER.warn("[WO Performance] Optional diagnostics unavailable; game behavior is preserved ({})",
                    failure.getClass().getSimpleName());
    }

    private static Map<String, String> environment(PerformanceDiagnosticsConfig.Values settings, Map<String, String> mods) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("minecraft", SharedConstants.getCurrentVersion().getName());
        metadata.put("java", System.getProperty("java.version", "unknown"));
        metadata.put("javaVm", System.getProperty("java.vm.name", "unknown"));
        metadata.put("nativeInstrumentationStartupEnabled", System.getProperty("wilderness.perf.instrumentation", "true"));
        metadata.put("jvmUptimeAtServerStartMillis", Long.toString(ManagementFactory.getRuntimeMXBean().getUptime()));
        metadata.put("tickWindow", Integer.toString(settings.windowTicks()));
        metadata.put("sampleIntervalTicks", Integer.toString(settings.sampleIntervalTicks()));
        metadata.put("chunkWaitThresholdMillis", Double.toString(settings.chunkWaitThresholdMillis()));
        metadata.put("chunkWaitStacks", Boolean.toString(settings.chunkWaitStacks()));
        // Arbitrary -D arguments may contain credentials; export only heap/GC settings.
        metadata.put("heapAndGcArguments", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-Xmx") || argument.startsWith("-Xms")
                        || argument.matches("-XX:[+-]Use[A-Za-z0-9]+GC"))
                .collect(java.util.stream.Collectors.joining(" ")));
        mods.forEach((mod, version) -> metadata.put("mod/" + mod, version));
        return Map.copyOf(metadata);
    }

    /** Session state consumes captured metadata and gauges; it performs no Minecraft lookups. */
    static final class Session {
        private final Object server;
        private final PerformanceDiagnosticsConfig.Values settings;
        private final String id = UUID.randomUUID().toString();
        private final String startedAt = Instant.now().toString();
        private final long startedNanos = System.nanoTime();
        private final PerformanceObserver observer;
        private final PerformanceTimings timings;
        private final JvmPerformanceSampler jvm = new JvmPerformanceSampler();
        private final Map<PerformanceArea, PerformanceOwnershipRegistry.Ownership> ownership;
        private final Map<String, String> environment;
        private final long waitThresholdNanos;
        private Map<String, Long> shutdownQueues = Map.of();
        private final ArrayDeque<PerformanceReport.Sample> history = new ArrayDeque<>();
        private boolean recording;
        private boolean jvmUnavailable;
        private boolean playerTickPending;
        private boolean playerTickRecorded;
        private int ticksSinceSample;
        private long tickStartNanos;
        private long stoppingNanos;

        Session(Object server, PerformanceDiagnosticsConfig.Values settings, Map<String, String> mods,
                Map<String, String> environment) {
            this.server = server;
            this.settings = settings;
            waitThresholdNanos = (long) (settings.chunkWaitThresholdMillis() * 1_000_000);
            observer = new PerformanceObserver(settings.windowTicks());
            timings = new PerformanceTimings(96, 32, (long) (settings.chunkWaitThresholdMillis() * 1_000_000), 10_000_000_000L);
            ownership = PerformanceOwnershipRegistry.detect(mods);
            recording = settings.recording() && settings.observation() != PerformanceDiagnosticsConfig.Mode.OFF;
            this.environment = Map.copyOf(environment);
        }

        boolean observes(Object candidate) {
            return server == candidate && settings.observation() != PerformanceDiagnosticsConfig.Mode.OFF;
        }

        boolean setRecording(boolean enabled) {
            if (settings.observation() == PerformanceDiagnosticsConfig.Mode.OFF) return false;
            recording = enabled;
            return true;
        }

        void addSample(long now, Map<String, Long> workQueues) {
            if (!recording) return;
            if (history.size() == 120) history.removeFirst();
            history.addLast(new PerformanceReport.Sample((now - startedNanos) / 1_000_000, observer.snapshot(), workQueues));
        }

        PerformanceReport report(String outcome, Map<String, Long> currentQueues) {
            Map<String, Long> workQueues = new HashMap<>(currentQueues);
            shutdownQueues.forEach((name, value) -> workQueues.put("atShutdown/" + name, value));
            return new PerformanceReport(1, id, startedAt, outcome, settings.safeMode(), settings.observation().name(),
                    environment, ownership, observer.snapshot(), timings.snapshot(), workQueues, List.copyOf(history),
                    "unavailable in Phase 0", List.of(
                    "Observation only: no save replacement, worker governor or chunk priority changes.",
                    "Tick percentiles cover the rolling window; they are not whole-trial percentiles.",
                    "GC collection time is a pressure proxy, not exact stop-the-world pause time.",
                    "Chunk timings cover managedBlock and off-thread join spans, not the whole getChunk call or generation throughput.",
                    "One off-thread request can produce both a caller span and a server span; counts are observed spans, not unique chunks.",
                    "Worldgen pressure, lighting stalls and save backlog are unavailable without additional proven hooks.",
                    "Startup begins at ServerAboutToStart; first-player tick is a server milestone, not a rendered playable frame.",
                    "Shutdown spans ServerStopping to ServerStopped; the client Save & Quit screen can include additional time.",
                    "Native timing mixins are optional. Absent phases can mean no match/call or bounded phase retention; inspect omittedPhaseMeasurements.",
                    "Queue counts cover shared/Background/Tick/Data systems; Data workers already belong to shared CPU totals.",
                    "Native and WO timing phases can nest; do not sum parent and child durations.",
                    "Third-party mod presence is verified; configurable patch activity is unverified.",
                    "Config contents, player data, service endpoints and credentials are excluded.",
                    "Client launch/resource milestones remain in logs/loading-stalls; no extra client profiler is created."));
        }
    }
}
