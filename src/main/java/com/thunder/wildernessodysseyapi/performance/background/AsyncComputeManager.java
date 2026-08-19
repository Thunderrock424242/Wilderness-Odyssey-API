package com.thunder.wildernessodysseyapi.performance.background;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Runs pure calculations on bounded workers and applies results on the server thread.
 *
 * <p>Callers must snapshot every required value before submission. Worker code
 * must not retain or touch levels, chunks, entities, block entities, registries,
 * or other mutable Minecraft objects. This manager never executes rejected work
 * on the caller and never blocks a server tick on a future.</p>
 */
public final class AsyncComputeManager {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private static final long FAILURE_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10L);

    private final BackgroundMetrics metrics;
    private final ConcurrentLinkedQueue<ApplyTask<?>> applyQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger applyBacklog = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> lastFailureLogs =
            new ConcurrentHashMap<>();

    private volatile Settings settings = Settings.defaults();
    private volatile ThreadPoolExecutor executor;

    public AsyncComputeManager(BackgroundMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Starts or replaces the worker pool using bounded queues and daemon threads. */
    public synchronized void initialize(Settings settings) {
        shutdown();
        this.settings = Objects.requireNonNull(settings, "settings").normalized();
        if (!this.settings.enabled()) {
            return;
        }

        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("WO-Background-Compute-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                this.settings.workerThreads(),
                this.settings.workerThreads(),
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(this.settings.maximumQueuedJobs()),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        running.set(true);
    }

    /**
     * Submits immutable data for worker computation and a server-thread result application.
     *
     * @param subsystem stable subsystem identifier used for metrics and failures
     * @param immutableSnapshot caller-created data snapshot containing no mutable Minecraft objects
     * @param computation pure or isolated worker calculation
     * @param resultApplier callback invoked later by {@link #drainServerThreadResults}
     * @return true when accepted by the bounded worker queue
     */
    public <S, R> boolean submit(
            String subsystem,
            S immutableSnapshot,
            CheckedFunction<S, R> computation,
            ServerResultApplier<R> resultApplier
    ) {
        String normalizedSubsystem = normalizeSubsystem(subsystem);
        Objects.requireNonNull(immutableSnapshot, "immutableSnapshot");
        Objects.requireNonNull(computation, "computation");
        Objects.requireNonNull(resultApplier, "resultApplier");
        ThreadPoolExecutor currentExecutor = executor;
        if (!running.get() || currentExecutor == null || !settings.enabled()) {
            metrics.recordAsyncRejected();
            return false;
        }

        try {
            currentExecutor.execute(() -> compute(
                    currentExecutor,
                    normalizedSubsystem,
                    immutableSnapshot,
                    computation,
                    resultApplier
            ));
            updateMetrics(currentExecutor);
            return true;
        } catch (RejectedExecutionException exception) {
            metrics.recordAsyncRejected();
            updateMetrics(currentExecutor);
            return false;
        }
    }

    /** Submits background IO or analytics work that has no Minecraft-state result. */
    public <S> boolean submitWithoutResult(
            String subsystem,
            S immutableSnapshot,
            CheckedConsumer<S> operation
    ) {
        Objects.requireNonNull(operation, "operation");
        String normalizedSubsystem = normalizeSubsystem(subsystem);
        Objects.requireNonNull(immutableSnapshot, "immutableSnapshot");
        ThreadPoolExecutor currentExecutor = executor;
        if (!running.get() || currentExecutor == null || !settings.enabled()) {
            metrics.recordAsyncRejected();
            return false;
        }
        try {
            currentExecutor.execute(() -> compute(currentExecutor, normalizedSubsystem, immutableSnapshot, snapshot -> {
                operation.accept(snapshot);
                return Boolean.TRUE;
            }, null));
            updateMetrics(currentExecutor);
            return true;
        } catch (RejectedExecutionException exception) {
            metrics.recordAsyncRejected();
            updateMetrics(currentExecutor);
            return false;
        }
    }

    /**
     * Applies completed results on the logical server thread without exceeding task or time limits.
     */
    public int drainServerThreadResults(MinecraftServer server, int maximumTasks, long deadlineNanos) {
        Objects.requireNonNull(server, "server");
        return drainResults(server, maximumTasks, deadlineNanos, ALWAYS_HAS_TIME);
    }

    /** Applies results only while Minecraft's live spare-time allowance remains. */
    public int drainServerThreadResults(
            MinecraftServer server,
            int maximumTasks,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(server, "server");
        return drainResults(server, maximumTasks, deadlineNanos, serverHasTime);
    }

    int drainResultsForTests(int maximumTasks, long deadlineNanos) {
        return drainResults(null, maximumTasks, deadlineNanos, ALWAYS_HAS_TIME);
    }

    private int drainResults(
            MinecraftServer server,
            int maximumTasks,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        int applied = 0;
        while (applied < Math.max(0, maximumTasks)
                && serverHasTime.getAsBoolean()
                && System.nanoTime() < deadlineNanos) {
            ApplyTask<?> task = applyQueue.poll();
            if (task == null) {
                break;
            }
            applyBacklog.updateAndGet(value -> Math.max(0, value - 1));
            try {
                task.apply(server);
            } catch (Exception exception) {
                metrics.recordAsyncFailed();
                logFailure(task.subsystem(), "server-thread application", exception);
            }
            applied++;
        }
        updateMetrics(executor);
        return applied;
    }

    /** Stops workers and discards results that can no longer safely target a server. */
    public synchronized void shutdown() {
        running.set(false);
        ThreadPoolExecutor currentExecutor = executor;
        executor = null;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        applyQueue.clear();
        applyBacklog.set(0);
        lastFailureLogs.clear();
        metrics.setAsyncState(0, 0);
    }

    /** Returns a cheap bounded-pool snapshot for diagnostics and tests. */
    public Snapshot snapshot() {
        ThreadPoolExecutor currentExecutor = executor;
        int active = currentExecutor == null ? 0 : currentExecutor.getActiveCount();
        int workerQueued = currentExecutor == null ? 0 : currentExecutor.getQueue().size();
        return new Snapshot(
                running.get() && settings.enabled(),
                settings.workerThreads(),
                settings.maximumQueuedJobs(),
                active,
                workerQueued,
                applyBacklog.get()
        );
    }

    private <S, R> void compute(
            ThreadPoolExecutor owningExecutor,
            String subsystem,
            S snapshot,
            CheckedFunction<S, R> computation,
            ServerResultApplier<R> resultApplier
    ) {
        try {
            // Pool identity prevents a slow, interruption-insensitive task from
            // publishing a stale result after a later server session starts.
            if (!running.get() || executor != owningExecutor) {
                return;
            }
            R result = computation.apply(snapshot);
            if (!running.get() || executor != owningExecutor) {
                return;
            }
            if (resultApplier == null) {
                metrics.recordAsyncCompleted();
                return;
            }
            int backlog = applyBacklog.incrementAndGet();
            if (backlog > settings.maximumQueuedJobs()) {
                applyBacklog.decrementAndGet();
                metrics.recordAsyncRejected();
                return;
            }
            applyQueue.offer(new ApplyTask<>(subsystem, result, resultApplier));
            metrics.recordAsyncCompleted();
        } catch (Exception exception) {
            metrics.recordAsyncFailed();
            logFailure(subsystem, "computation", exception);
        } finally {
            updateMetrics(executor);
        }
    }

    private void updateMetrics(ThreadPoolExecutor currentExecutor) {
        int active = currentExecutor == null ? 0 : currentExecutor.getActiveCount();
        int queued = (currentExecutor == null ? 0 : currentExecutor.getQueue().size()) + applyBacklog.get();
        metrics.setAsyncState(active, queued);
    }

    private void logFailure(String subsystem, String phase, Exception exception) {
        String signature = subsystem + ':' + phase + ':' + exception.getClass().getName();
        java.util.concurrent.atomic.AtomicLong lastLog =
                lastFailureLogs.computeIfAbsent(signature, ignored -> new java.util.concurrent.atomic.AtomicLong());
        long now = System.nanoTime();
        long previous = lastLog.get();
        if ((previous == 0L || now - previous >= FAILURE_LOG_INTERVAL_NANOS)
                && lastLog.compareAndSet(previous, now)) {
            ModConstants.LOGGER.error(
                    "[Background Async] {} failed for subsystem '{}'",
                    phase,
                    subsystem,
                    exception
            );
        }
    }

    private static String normalizeSubsystem(String subsystem) {
        String value = Objects.requireNonNullElse(subsystem, "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        return value.isEmpty() ? "unknown" : value;
    }

    /** Bounded worker configuration independent of the config-file implementation. */
    public record Settings(boolean enabled, int workerThreads, int maximumQueuedJobs) {
        public static Settings defaults() {
            int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
            return new Settings(true, Math.max(1, Math.min(4, processors - 2)), 128);
        }

        private Settings normalized() {
            return new Settings(enabled, Math.max(1, Math.min(16, workerThreads)),
                    Math.max(1, maximumQueuedJobs));
        }
    }

    /** Immutable current executor state. */
    public record Snapshot(
            boolean enabled,
            int workerThreads,
            int maximumQueuedJobs,
            int activeJobs,
            int workerQueueSize,
            int applyQueueSize
    ) {
    }

    /** Worker computation that may report a checked failure. */
    @FunctionalInterface
    public interface CheckedFunction<S, R> {
        R apply(S snapshot) throws Exception;
    }

    /** Worker-side operation with no Minecraft-state result. */
    @FunctionalInterface
    public interface CheckedConsumer<S> {
        void accept(S snapshot) throws Exception;
    }

    /** Server-thread application of a completed immutable result. */
    @FunctionalInterface
    public interface ServerResultApplier<R> {
        void apply(MinecraftServer server, R result) throws Exception;
    }

    private record ApplyTask<R>(String subsystem, R result, ServerResultApplier<R> applier) {
        private void apply(MinecraftServer server) throws Exception {
            applier.apply(server, result);
        }
    }
}
