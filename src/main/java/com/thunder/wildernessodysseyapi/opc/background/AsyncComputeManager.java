package com.thunder.wildernessodysseyapi.opc.background;

import com.thunder.wildernessodysseyapi.opc.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Snapshot/compute/apply compatibility service using AsyncTaskManager's bounded CPU workers.
 * Callers capture immutable inputs and apply results only on the server thread. This service
 * owns a bounded result lane and session token, never a thread pool or caller-runs fallback.
 */
public final class AsyncComputeManager {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private final BackgroundMetrics metrics;
    private volatile Settings settings = Settings.defaults();
    private volatile Session current;

    public AsyncComputeManager(BackgroundMetrics metrics) { this.metrics = Objects.requireNonNull(metrics); }

    /** Starts a new admission lane; shared workers must be initialized by the server lifecycle. */
    public synchronized void initialize(Settings settings) {
        shutdown();
        this.settings = Objects.requireNonNull(settings).normalized();
        if (this.settings.enabled()) current = new Session(this.settings);
    }

    public <S, R> boolean submit(String subsystem, S immutableSnapshot, CheckedFunction<S, R> computation,
                                 ServerResultApplier<R> resultApplier) {
        return submitInternal(subsystem, immutableSnapshot, computation, Objects.requireNonNull(resultApplier));
    }

    /** Optional detached work; required persistence must use AsyncTaskManager's required IO lane. */
    public <S> boolean submitWithoutResult(String subsystem, S immutableSnapshot, CheckedConsumer<S> operation) {
        Objects.requireNonNull(operation);
        return submitInternal(subsystem, immutableSnapshot, value -> { operation.accept(value); return Boolean.TRUE; }, null);
    }

    private <S, R> boolean submitInternal(String subsystem, S snapshot, CheckedFunction<S, R> computation,
                                         ServerResultApplier<R> applier) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(computation);
        String name = normalizeSubsystem(subsystem);
        Session session = current;
        if (session == null || !OdysseyPerformanceCore.backgroundAllowed()) return rejected();
        synchronized (session) {
            // Count pending applications too: slow server drains cannot retain unbounded snapshots.
            if (current != session || session.outstanding >= session.capacity) return rejected();
            session.outstanding++;
            session.queued++;
        }
        boolean accepted = AsyncTaskManager.trySubmitCpuWork("background/" + name,
                () -> compute(session, name, snapshot, computation, applier));
        if (!accepted) {
            synchronized (session) { session.queued--; session.outstanding--; }
            return rejected();
        }
        updateMetrics();
        return true;
    }

    private boolean rejected() { metrics.recordAsyncRejected(); return false; }

    private <S, R> void compute(Session session, String name, S snapshot, CheckedFunction<S, R> computation,
                                 ServerResultApplier<R> applier) {
        synchronized (session) {
            session.queued--;
            if (current != session) { session.outstanding--; return; }
            session.active++;
        }
        boolean published = false;
        try {
            R result = computation.apply(snapshot);
            synchronized (session) {
                if (current != session) return;
                if (applier != null) {
                    // Outstanding admission already reserved room for this result. Result capacity
                    // includes the legacy active+queued allowance, so accepted callbacks are retained.
                    session.results.addLast(new ApplyTask<>(name, result, applier));
                    published = true;
                }
                metrics.recordAsyncCompleted();
            }
        } catch (Exception failure) {
            if (current == session) { metrics.recordAsyncFailed(); logFailure(session, name, "computation", failure); }
        } finally {
            synchronized (session) {
                session.active--;
                if (!published) session.outstanding--;
            }
            updateMetrics();
        }
    }

    public int drainServerThreadResults(MinecraftServer server, int maximumTasks, long deadlineNanos) {
        return drainServerThreadResults(server, maximumTasks, deadlineNanos, ALWAYS_HAS_TIME);
    }
    public int drainServerThreadResults(MinecraftServer server, int maximumTasks, long deadlineNanos, BooleanSupplier hasTime) {
        return drainResults(Objects.requireNonNull(server), maximumTasks, deadlineNanos, hasTime);
    }
    int drainResultsForTests(int maximumTasks, long deadlineNanos) { return drainResults(null, maximumTasks, deadlineNanos, ALWAYS_HAS_TIME); }

    private int drainResults(MinecraftServer server, int maximumTasks, long deadlineNanos, BooleanSupplier hasTime) {
        Objects.requireNonNull(hasTime);
        Session session = current;
        if (session == null) return 0;
        int applied = 0;
        while (applied < Math.max(0, maximumTasks) && hasTime.getAsBoolean() && System.nanoTime() < deadlineNanos) {
            ApplyTask<?> task;
            synchronized (session) {
                if (current != session) break;
                task = session.results.pollFirst();
                if (task == null) break;
                session.outstanding--;
            }
            try { task.apply(server); }
            catch (Exception failure) { metrics.recordAsyncFailed(); logFailure(session, task.subsystem(), "server application", failure); }
            applied++;
        }
        updateMetrics();
        return applied;
    }

    /** Invalidates queued/running computations and results without cancelling shared required work. */
    public synchronized void shutdown() {
        Session previous = current;
        current = null;
        if (previous != null) synchronized (previous) { previous.results.clear(); previous.lastFailureLogs.clear(); }
        metrics.setAsyncState(0, 0);
    }

    public Snapshot snapshot() {
        Session session = current;
        if (session == null) return new Snapshot(false, settings.workerThreads(), settings.maximumQueuedJobs(), 0, 0, 0);
        synchronized (session) {
            return new Snapshot(true, settings.workerThreads(), settings.maximumQueuedJobs(), session.active, session.queued, session.results.size());
        }
    }

    private void updateMetrics() {
        Session session = current;
        if (session == null) return;
        synchronized (session) {
            if (current == session) metrics.setAsyncState(session.active, session.queued + session.results.size());
        }
    }

    private void logFailure(Session session, String subsystem, String phase, Exception failure) {
        String key = subsystem + ':' + phase + ':' + failure.getClass().getName();
        long now = System.nanoTime();
        synchronized (session) {
            Long previous = session.lastFailureLogs.get(key);
            if (previous != null && now - previous < TimeUnit.SECONDS.toNanos(10)) return;
            if (session.lastFailureLogs.size() >= 128) session.lastFailureLogs.clear();
            session.lastFailureLogs.put(key, now);
        }
        ModConstants.LOGGER.error("[OPC Background] {} failed for subsystem '{}'", phase, subsystem, failure);
    }

    private static String normalizeSubsystem(String subsystem) {
        String value = Objects.requireNonNullElse(subsystem, "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        return value.isEmpty() ? "unknown" : value;
    }

    /** workerThreads is a legacy admission weight; physical CPU workers belong to AsyncTaskManager. */
    public record Settings(boolean enabled, int workerThreads, int maximumQueuedJobs) {
        public static Settings defaults() {
            return new Settings(true, Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)), 128);
        }
        private Settings normalized() { return new Settings(enabled, Math.max(1, Math.min(16, workerThreads)), Math.max(1, maximumQueuedJobs)); }
    }
    public record Snapshot(boolean enabled, int workerThreads, int maximumQueuedJobs, int activeJobs, int workerQueueSize, int applyQueueSize) { }
    @FunctionalInterface public interface CheckedFunction<S, R> { R apply(S snapshot) throws Exception; }
    @FunctionalInterface public interface CheckedConsumer<S> { void accept(S snapshot) throws Exception; }
    @FunctionalInterface public interface ServerResultApplier<R> { void apply(MinecraftServer server, R result) throws Exception; }
    private record ApplyTask<R>(String subsystem, R result, ServerResultApplier<R> applier) {
        private void apply(MinecraftServer server) throws Exception { applier.apply(server, result); }
    }
    private static final class Session {
        private final long capacity;
        private final ArrayDeque<ApplyTask<?>> results = new ArrayDeque<>();
        private final Map<String, Long> lastFailureLogs = new HashMap<>();
        private int outstanding;
        private int queued;
        private int active;
        private Session(Settings settings) { capacity = (long) settings.workerThreads() + settings.maximumQueuedJobs(); }
    }
}
