package com.thunder.wildernessodysseyapi.async;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Manages worker threads and safe handoff to the logical server thread.
 */
public final class AsyncTaskManager {

    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final ConcurrentLinkedQueue<QueuedMainThreadTask> MAIN_THREAD_QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<ThreadPoolExecutor> RETIRED_EXECUTORS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger MAIN_THREAD_BACKLOG = new AtomicInteger();
    private static final AtomicInteger REJECTED = new AtomicInteger();
    // Retained in diagnostics for API compatibility. Rejected work is never
    // executed inline, so this counter remains zero.
    private static final AtomicInteger CALLER_RUNS = new AtomicInteger();
    private static final AtomicLong LAST_DIRECT_REJECTION_WARNING_NANOS = new AtomicLong();
    private static final AtomicLong LIFECYCLE_GENERATION = new AtomicLong();
    private static final AtomicLong STALE_RESULTS = new AtomicLong();
    private static final AtomicLong LAST_STALE_RESULT_WARNING_NANOS = new AtomicLong();

    private static final int THREAD_KEEP_ALIVE_SECONDS = 45;
    private static final long DIRECT_REJECTION_WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private static volatile AsyncThreadingConfig.AsyncConfigValues configValues = AsyncThreadingConfig.values();

    private static volatile ThreadPoolExecutor cpuExecutor;
    private static volatile ThreadPoolExecutor ioExecutor;

    private static volatile int appliedLastTick = 0;

    private AsyncTaskManager() {
    }

    /**
     * Initializes worker executors using the current config values.
     */
    public static synchronized void initialize(AsyncThreadingConfig.AsyncConfigValues config) {
        shutdown();
        configValues = Objects.requireNonNull(config, "config");

        if (!config.enabled()) {
            ModConstants.LOGGER.info("[Async] Async task system disabled via config.");
            INITIALIZED.set(false);
            return;
        }

        cpuExecutor = buildExecutor("WO-Async-CPU", config.maxThreads(), config.queueSize());
        ioExecutor = buildExecutor("WO-Async-IO", Math.max(4, config.maxThreads() * 2), config.queueSize());
        INITIALIZED.set(true);
        ModConstants.LOGGER.info("[Async] Initialized with {} worker threads and queue size {}.", config.maxThreads(), config.queueSize());
    }

    /**
     * Applies live configuration without clearing server-thread callbacks or
     * cancelling work that was already accepted by the previous executors.
     *
     * <p>The owning server thread must call this method. New submissions are
     * directed to the replacement pools immediately, while the retired pools
     * finish their already accepted tasks and then terminate.</p>
     */
    public static synchronized void reload(AsyncThreadingConfig.AsyncConfigValues config) {
        Objects.requireNonNull(config, "config");
        RETIRED_EXECUTORS.removeIf(ThreadPoolExecutor::isTerminated);

        ThreadPoolExecutor previousCpuExecutor = cpuExecutor;
        ThreadPoolExecutor previousIoExecutor = ioExecutor;
        configValues = config;

        if (config.enabled()) {
            cpuExecutor = buildExecutor("WO-Async-CPU", config.maxThreads(), config.queueSize());
            ioExecutor = buildExecutor("WO-Async-IO", Math.max(4, config.maxThreads() * 2), config.queueSize());
            INITIALIZED.set(true);
            ModConstants.LOGGER.info(
                    "[Async] Reloaded with {} worker threads and queue size {}; accepted work remains on retired pools.",
                    config.maxThreads(),
                    config.queueSize()
            );
        } else {
            cpuExecutor = null;
            ioExecutor = null;
            INITIALIZED.set(false);
            ModConstants.LOGGER.info("[Async] Disabled new async submissions; accepted work is draining.");
        }

        // shutdown() is deliberately non-blocking here. A live config reload
        // must not spend up to four seconds waiting on workers from the server thread.
        retireExecutor(previousCpuExecutor);
        retireExecutor(previousIoExecutor);
    }

    /**
     * Stops executors and clears queued tasks.
     */
    public static synchronized void shutdown() {
        // Invalidate in-flight result producers before stopping their executors.
        // A task that ignores interruption can no longer enqueue work for the
        // next integrated or dedicated server instance.
        LIFECYCLE_GENERATION.incrementAndGet();
        shutdownExecutor(cpuExecutor);
        shutdownExecutor(ioExecutor);
        ThreadPoolExecutor retiredExecutor;
        while ((retiredExecutor = RETIRED_EXECUTORS.poll()) != null) {
            shutdownExecutor(retiredExecutor);
        }
        cpuExecutor = null;
        ioExecutor = null;
        MAIN_THREAD_QUEUE.clear();
        MAIN_THREAD_BACKLOG.set(0);
        appliedLastTick = 0;
        CALLER_RUNS.set(0);
        LAST_DIRECT_REJECTION_WARNING_NANOS.set(0L);
        INITIALIZED.set(false);
    }

    public static CompletableFuture<Boolean> submitCpuTask(String label, TaskPayload taskPayload) {
        return submitTask(cpuExecutor, label, taskPayload);
    }

    public static CompletableFuture<Boolean> submitIoTask(String label, TaskPayload taskPayload) {
        return submitTask(ioExecutor, label, taskPayload);
    }

    /**
     * Non-blockingly submits pure CPU work without a main-thread callback.
     *
     * <p>This path is used by bounded services such as the Data Engine. It
     * uses the executor's bounded abort policy so saturation can never invoke
     * a caller-runs fallback on the Minecraft thread.
     * The task must not read or mutate live world/entity state.</p>
     *
     * @return {@code true} when the bounded worker queue accepted the task
     */
    public static boolean trySubmitCpuWork(String label, Runnable task) {
        return trySubmitWork(cpuExecutor, label, task, "CPU");
    }

    /**
     * Non-blockingly submits fire-and-forget I/O work.
     *
     * <p>Unlike a caller-runs rejection policy, saturation returns
     * {@code false} immediately. Callers can then coalesce, retain, or drop
     * optional work without moving disk or network latency onto a tick thread.</p>
     */
    public static boolean trySubmitIoWork(String label, Runnable task) {
        return trySubmitWork(ioExecutor, label, task, "I/O");
    }

    private static CompletableFuture<Boolean> submitTask(ThreadPoolExecutor executor, String label, TaskPayload taskPayload) {
        AsyncThreadingConfig.AsyncConfigValues submissionConfig = configValues;
        if (!submissionConfig.enabled() || executor == null || !INITIALIZED.get() || executor.isShutdown()) {
            return CompletableFuture.completedFuture(false);
        }

        Objects.requireNonNull(taskPayload, "taskPayload");
        try {
            long submissionGeneration = LIFECYCLE_GENERATION.get();
            AtomicBoolean timedOut = new AtomicBoolean(false);
            CompletableFuture<Boolean> workerFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Optional<MainThreadTask> result = taskPayload.createResult();
                    if (timedOut.get()) {
                        return false;
                    }
                    if (result.isEmpty()) {
                        return true;
                    }
                    return !timedOut.get()
                            && enqueueMainThreadTask(label, result.get(), submissionGeneration);
                } catch (Exception e) {
                    ModConstants.LOGGER.error("[Async] Task '{}' failed", label, e);
                    return false;
                }
            }, executor);

            if (submissionConfig.taskTimeoutMs() > 0) {
                return workerFuture.orTimeout(submissionConfig.taskTimeoutMs(), TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            if (ex instanceof TimeoutException) {
                                timedOut.set(true);
                                workerFuture.cancel(true);
                                ModConstants.LOGGER.warn("[Async] Task '{}' timed out after {} ms", label, submissionConfig.taskTimeoutMs());
                            } else {
                                ModConstants.LOGGER.error("[Async] Task '{}' failed", label, ex);
                            }
                            return false;
                        });
            }
            return workerFuture;
        } catch (RejectedExecutionException ex) {
            int rejected = REJECTED.incrementAndGet();
            ModConstants.LOGGER.warn("[Async] Rejected task '{}' ({} queued, total rejections: {}).", label, executor.getQueue().size(), rejected);
            return CompletableFuture.completedFuture(false);
        }
    }

    static boolean enqueueMainThreadTask(String label, MainThreadTask task, long submissionGeneration) {
        Objects.requireNonNull(task, "task");
        if (submissionGeneration != LIFECYCLE_GENERATION.get()) {
            recordStaleResult(label);
            return false;
        }
        int backlog = MAIN_THREAD_BACKLOG.incrementAndGet();
        int maxQueue = configValues.queueSize();
        if (backlog > maxQueue) {
            MAIN_THREAD_BACKLOG.decrementAndGet();
            REJECTED.incrementAndGet();
            ModConstants.LOGGER.warn("[Async] Main-thread queue full ({}). Dropping task '{}'.", maxQueue, label);
            return false;
        }

        QueuedMainThreadTask queuedTask = new QueuedMainThreadTask(label, submissionGeneration, task);
        boolean offered = MAIN_THREAD_QUEUE.offer(queuedTask);
        if (!offered) {
            MAIN_THREAD_BACKLOG.decrementAndGet();
            REJECTED.incrementAndGet();
            ModConstants.LOGGER.warn("[Async] Failed to enqueue main-thread task '{}'.", label);
        } else if (configValues.debugLogging()) {
            ModConstants.LOGGER.info("[Async] Queued main-thread task '{}' (backlog: {}).", label, backlog);
        }
        if (offered && submissionGeneration != LIFECYCLE_GENERATION.get()) {
            if (MAIN_THREAD_QUEUE.remove(queuedTask)) {
                MAIN_THREAD_BACKLOG.decrementAndGet();
            }
            recordStaleResult(label);
            return false;
        }
        return offered;
    }

    public static void drainMainThreadQueue(MinecraftServer server) {
        drainMainThreadQueue(server, ALWAYS_HAS_TIME);
    }

    /**
     * Applies optional worker results only while Minecraft reports spare tick time.
     *
     * <p>The live supplier is checked before every callback so this queue yields
     * to server-owned chunk IO and generation completion as soon as the server's
     * allowance expires.</p>
     */
    public static void drainMainThreadQueue(MinecraftServer server, BooleanSupplier serverHasTime) {
        if (server == null) {
            return;
        }
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        int maxTasks = Math.max(1, configValues.applyPerTick());
        int processed = 0;
        while (processed < maxTasks && serverHasTime.getAsBoolean()) {
            QueuedMainThreadTask queuedTask = MAIN_THREAD_QUEUE.poll();
            if (queuedTask == null) {
                break;
            }
            MAIN_THREAD_BACKLOG.decrementAndGet();
            if (queuedTask.generation() != LIFECYCLE_GENERATION.get()) {
                recordStaleResult(queuedTask.label());
                continue;
            }
            try {
                queuedTask.task().run(server);
            } catch (Exception e) {
                ModConstants.LOGGER.error("[Async] Error applying main-thread task", e);
            }
            processed++;
        }
        appliedLastTick = processed;
    }

    public static AsyncTaskStats snapshot() {
        int active = cpuExecutor == null ? 0 : cpuExecutor.getActiveCount();
        int workerQueue = cpuExecutor == null ? 0 : cpuExecutor.getQueue().size();
        int backlog = Math.max(0, MAIN_THREAD_BACKLOG.get());
        return new AsyncTaskStats(
                configValues.enabled() && INITIALIZED.get(),
                configValues.maxThreads(),
                configValues.queueSize(),
                active,
                workerQueue,
                backlog,
                appliedLastTick,
                REJECTED.get(),
                CALLER_RUNS.get()
        );
    }

    /** Returns the current CPU worker queue length without allocating a diagnostic snapshot. */
    public static int queuedCpuWorkTasks() {
        ThreadPoolExecutor executor = cpuExecutor;
        return executor == null ? 0 : executor.getQueue().size();
    }

    private static ThreadPoolExecutor buildExecutor(String prefix, int threads, int queueSize) {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize);
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable);
            t.setName(prefix + t.threadId());
            t.setDaemon(true);
            return t;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads,
                threads,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                queue,
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static boolean trySubmitWork(ThreadPoolExecutor executor, String label, Runnable task, String workType) {
        Objects.requireNonNull(label, "Task label is required");
        Objects.requireNonNull(task, "Task is required");
        if (!configValues.enabled() || executor == null || !INITIALIZED.get() || executor.isShutdown()) {
            recordDirectRejection(executor, label, workType);
            return false;
        }

        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    ModConstants.LOGGER.error("[Async] {} work '{}' failed", workType, label, exception);
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            recordDirectRejection(executor, label, workType);
            return false;
        }
    }

    private static void recordDirectRejection(ThreadPoolExecutor executor, String label, String workType) {
        int rejected = REJECTED.incrementAndGet();
        long nowNanos = System.nanoTime();
        long previousWarning = LAST_DIRECT_REJECTION_WARNING_NANOS.get();
        if (nowNanos - previousWarning >= DIRECT_REJECTION_WARNING_INTERVAL_NANOS
                && LAST_DIRECT_REJECTION_WARNING_NANOS.compareAndSet(previousWarning, nowNanos)) {
            int queued = executor == null ? 0 : executor.getQueue().size();
            ModConstants.LOGGER.warn(
                    "[Async] {} work '{}' rejected without caller-thread fallback ({} queued, {} total rejections).",
                    workType,
                    label,
                    queued,
                    rejected
            );
        }
    }

    private static void recordStaleResult(String label) {
        long staleResults = STALE_RESULTS.incrementAndGet();
        long nowNanos = System.nanoTime();
        long previousWarning = LAST_STALE_RESULT_WARNING_NANOS.get();
        if (nowNanos - previousWarning >= DIRECT_REJECTION_WARNING_INTERVAL_NANOS
                && LAST_STALE_RESULT_WARNING_NANOS.compareAndSet(previousWarning, nowNanos)) {
            ModConstants.LOGGER.warn(
                    "[Async] Skipped stale main-thread result '{}' after server lifecycle changed ({} total).",
                    label,
                    staleResults
            );
        }
    }

    static long lifecycleGeneration() {
        return LIFECYCLE_GENERATION.get();
    }

    private static void retireExecutor(ThreadPoolExecutor executor) {
        if (executor != null) {
            executor.shutdown();
            if (!executor.isTerminated()) {
                RETIRED_EXECUTORS.add(executor);
            }
        }
    }

    private static void shutdownExecutor(ThreadPoolExecutor executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public static AsyncThreadingConfig.AsyncConfigValues getConfigValues() {
        return configValues;
    }

    @FunctionalInterface
    public interface TaskPayload {
        Optional<MainThreadTask> createResult() throws Exception;
    }

    private record QueuedMainThreadTask(String label, long generation, MainThreadTask task) {
    }
}
