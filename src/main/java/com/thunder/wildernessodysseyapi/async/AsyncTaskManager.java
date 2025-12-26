package com.thunder.wildernessodysseyapi.async;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages worker threads and safe handoff to the logical server thread.
 */
public final class AsyncTaskManager {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final ConcurrentLinkedQueue<MainThreadTask> MAIN_THREAD_QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger MAIN_THREAD_BACKLOG = new AtomicInteger();
    private static final AtomicInteger REJECTED = new AtomicInteger();
    private static final AtomicInteger CALLER_RUNS = new AtomicInteger();

    private static final int THREAD_KEEP_ALIVE_SECONDS = 45;

    private static AsyncThreadingConfig.AsyncConfigValues configValues = AsyncThreadingConfig.values();

    private static ThreadPoolExecutor cpuExecutor;
    private static ThreadPoolExecutor ioExecutor;

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
        ioExecutor = buildExecutor("WO-Async-IO", Math.max(1, Math.min(2, config.maxThreads())), config.queueSize());
        INITIALIZED.set(true);
        ModConstants.LOGGER.info("[Async] Initialized with {} worker threads and queue size {}.", config.maxThreads(), config.queueSize());
    }

    /**
     * Stops executors and clears queued tasks.
     */
    public static synchronized void shutdown() {
        shutdownExecutor(cpuExecutor);
        shutdownExecutor(ioExecutor);
        cpuExecutor = null;
        ioExecutor = null;
        MAIN_THREAD_QUEUE.clear();
        MAIN_THREAD_BACKLOG.set(0);
        appliedLastTick = 0;
        CALLER_RUNS.set(0);
        INITIALIZED.set(false);
    }

    public static CompletableFuture<Boolean> submitCpuTask(String label, TaskPayload taskPayload) {
        return submitTask(cpuExecutor, label, taskPayload);
    }

    public static CompletableFuture<Boolean> submitIoTask(String label, TaskPayload taskPayload) {
        return submitTask(ioExecutor, label, taskPayload);
    }

    private static CompletableFuture<Boolean> submitTask(ThreadPoolExecutor executor, String label, TaskPayload taskPayload) {
        if (!configValues.enabled() || executor == null || !INITIALIZED.get()) {
            return CompletableFuture.completedFuture(false);
        }

        Objects.requireNonNull(taskPayload, "taskPayload");
        try {
            AtomicBoolean timedOut = new AtomicBoolean(false);
            CompletableFuture<Boolean> workerFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Optional<MainThreadTask> result = taskPayload.createResult();
                    if (timedOut.get()) {
                        return false;
                    }
                    return result.filter(task -> !timedOut.get() && enqueueMainThreadTask(label, task)).isPresent();
                } catch (Exception e) {
                    ModConstants.LOGGER.error("[Async] Task '{}' failed", label, e);
                    return false;
                }
            }, executor);

            if (configValues.taskTimeoutMs() > 0) {
                return workerFuture.orTimeout(configValues.taskTimeoutMs(), TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            if (ex instanceof TimeoutException) {
                                timedOut.set(true);
                                workerFuture.cancel(true);
                                ModConstants.LOGGER.warn("[Async] Task '{}' timed out after {} ms", label, configValues.taskTimeoutMs());
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

    private static boolean enqueueMainThreadTask(String label, MainThreadTask task) {
        Objects.requireNonNull(task, "task");
        int backlog = MAIN_THREAD_BACKLOG.incrementAndGet();
        int maxQueue = configValues.queueSize();
        if (backlog > maxQueue) {
            MAIN_THREAD_BACKLOG.decrementAndGet();
            REJECTED.incrementAndGet();
            ModConstants.LOGGER.warn("[Async] Main-thread queue full ({}). Dropping task '{}'.", maxQueue, label);
            return false;
        }

        boolean offered = MAIN_THREAD_QUEUE.offer(task);
        if (!offered) {
            MAIN_THREAD_BACKLOG.decrementAndGet();
            REJECTED.incrementAndGet();
            ModConstants.LOGGER.warn("[Async] Failed to enqueue main-thread task '{}'.", label);
        } else if (configValues.debugLogging()) {
            ModConstants.LOGGER.info("[Async] Queued main-thread task '{}' (backlog: {}).", label, backlog);
        }
        return offered;
    }

    public static void drainMainThreadQueue(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int maxTasks = Math.max(1, configValues.applyPerTick());
        int processed = 0;
        while (processed < maxTasks) {
            MainThreadTask task = MAIN_THREAD_QUEUE.poll();
            if (task == null) {
                break;
            }
            MAIN_THREAD_BACKLOG.decrementAndGet();
            try {
                task.run(server);
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
                callerRunsWithBackoff(prefix)
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static RejectedExecutionHandler callerRunsWithBackoff(String prefix) {
        AtomicLong lastLoggedNanos = new AtomicLong();
        return (task, executor) -> {
            int callerRuns = CALLER_RUNS.incrementAndGet();
            long now = System.nanoTime();
            long last = lastLoggedNanos.get();
            if (now - last > TimeUnit.SECONDS.toNanos(5) && lastLoggedNanos.compareAndSet(last, now)) {
                ModConstants.LOGGER.warn(
                        "[Async] Executor '{}' saturated (active: {}, queued: {}). Running task on caller thread ({} total).",
                        prefix,
                        executor.getActiveCount(),
                        executor.getQueue().size(),
                        callerRuns);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        };
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
}
