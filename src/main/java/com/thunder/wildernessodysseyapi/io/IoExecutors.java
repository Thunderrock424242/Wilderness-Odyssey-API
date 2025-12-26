package com.thunder.wildernessodysseyapi.io;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamingConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated executors for disk/network I/O with optional per-dimension isolation.
 */
public final class IoExecutors {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static ChunkStreamingConfig.ChunkConfigValues config = ChunkStreamingConfig.values();
    private static ThreadPoolExecutor sharedExecutor;
    private static final ConcurrentMap<ResourceKey<Level>, ThreadPoolExecutor> DIMENSION_EXECUTORS = new ConcurrentHashMap<>();

    private IoExecutors() {
    }

    public static synchronized void initialize(ChunkStreamingConfig.ChunkConfigValues values) {
        Objects.requireNonNull(values, "values");
        shutdown();
        config = values;
        sharedExecutor = buildExecutor("WO-IO-Shared", values.ioThreads(), values.ioQueueSize());
        INITIALIZED.set(true);
        ModConstants.LOGGER.info("[IO] Initialized executors (per-dimension: {}, threads: {}, queue: {}).",
                values.perDimensionExecutors(), values.ioThreads(), values.ioQueueSize());
    }

    public static synchronized void shutdown() {
        shutdownExecutor(sharedExecutor);
        sharedExecutor = null;
        DIMENSION_EXECUTORS.values().forEach(IoExecutors::shutdownExecutor);
        DIMENSION_EXECUTORS.clear();
        INITIALIZED.set(false);
    }

    public static CompletableFuture<Void> submit(ResourceKey<Level> dimension, String label, Runnable task) {
        ThreadPoolExecutor executor = executorFor(dimension);
        if (executor == null) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        try {
            return CompletableFuture.runAsync(task, executor);
        } catch (RejectedExecutionException ex) {
            ModConstants.LOGGER.warn("[IO] Executor '{}' rejected task '{}'; running synchronously.",
                    executor.getThreadFactory(), label);
            task.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static ThreadPoolExecutor executorFor(ResourceKey<Level> dimension) {
        if (!INITIALIZED.get()) {
            return null;
        }
        if (config.perDimensionExecutors() && dimension != null) {
            return DIMENSION_EXECUTORS.computeIfAbsent(dimension, key -> {
                String suffix = key.location().getPath().replace(':', '_');
                return buildExecutor("WO-IO-" + suffix + "-", config.ioThreads(), config.ioQueueSize());
            });
        }
        return sharedExecutor;
    }

    private static ThreadPoolExecutor buildExecutor(String prefix, int threads, int queueSize) {
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName(prefix + thread.getId());
                thread.setDaemon(true);
                return thread;
            }

            @Override
            public String toString() {
                return prefix;
            }
        };
        RejectedExecutionHandler rejectionHandler = callerRunsWithLogging(prefix);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads,
                threads,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                factory,
                rejectionHandler
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static RejectedExecutionHandler callerRunsWithLogging(String prefix) {
        AtomicLong lastLoggedNanos = new AtomicLong();
        return (task, executor) -> {
            long now = System.nanoTime();
            long last = lastLoggedNanos.get();
            if (now - last > TimeUnit.SECONDS.toNanos(5) && lastLoggedNanos.compareAndSet(last, now)) {
                ModConstants.LOGGER.warn(
                        "[IO] Executor '{}' saturated (active: {}, queued: {}). Running tasks on caller thread.",
                        prefix,
                        executor.getActiveCount(),
                        executor.getQueue().size());
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
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
