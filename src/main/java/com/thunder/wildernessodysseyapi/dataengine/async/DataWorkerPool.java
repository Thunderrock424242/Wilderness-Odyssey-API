package com.thunder.wildernessodysseyapi.dataengine.async;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetrics;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlled Data Engine bridge into the mod's existing CPU worker executor.
 *
 * <p>No executor is duplicated. Submissions are bounded by both an in-flight
 * limit and the shared executor queue, and saturation never runs calculation on
 * the caller/server thread. Completed results wait in {@link CompletedTaskQueue}
 * for server-thread validation and application.</p>
 */
public final class DataWorkerPool {
    private final CompletedTaskQueue completedTasks;
    private final DataEngineMetrics metrics;
    private final int maximumInFlight;
    private final WorkSubmitter submitter;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public DataWorkerPool(
            CompletedTaskQueue completedTasks,
            DataEngineMetrics metrics,
            int maximumInFlight
    ) {
        this(completedTasks, metrics, maximumInFlight, AsyncTaskManager::trySubmitCpuWork);
    }

    DataWorkerPool(
            CompletedTaskQueue completedTasks,
            DataEngineMetrics metrics,
            int maximumInFlight,
            WorkSubmitter submitter
    ) {
        this.completedTasks = Objects.requireNonNull(completedTasks, "Completed queue is required");
        this.metrics = Objects.requireNonNull(metrics, "Data Engine metrics are required");
        if (maximumInFlight < 1) {
            throw new IllegalArgumentException("Maximum in-flight tasks must be positive");
        }
        this.maximumInFlight = maximumInFlight;
        this.submitter = Objects.requireNonNull(submitter, "Worker submitter is required");
    }

    /**
     * Submits immutable-snapshot calculation and returns false on explicit
     * backpressure. The authoritative owner may retain/retry its dirty state.
     */
    public <R> boolean submit(
            ResourceLocation systemId,
            String label,
            UpdatePriority priority,
            boolean supersedable,
            AsyncDataTask<R> task
    ) {
        Objects.requireNonNull(systemId, "System id is required");
        Objects.requireNonNull(label, "Task label is required");
        Objects.requireNonNull(priority, "Task priority is required");
        Objects.requireNonNull(task, "Async task is required");
        if (!accepting.get()) {
            metrics.recordAsyncRejected();
            return false;
        }
        int current = inFlight.incrementAndGet();
        if (current > maximumInFlight) {
            inFlight.decrementAndGet();
            metrics.recordAsyncRejected();
            return false;
        }

        metrics.recordAsyncSubmitted();
        boolean accepted = submitter.submit("DataEngine_" + label, () -> compute(systemId, priority, supersedable, task));
        if (!accepted) {
            inFlight.decrementAndGet();
            metrics.recordAsyncRejected();
        }
        return accepted;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int maximumInFlight() {
        return maximumInFlight;
    }

    /** Stops new submissions and prevents late worker results entering the apply queue. */
    public void shutdown() {
        accepting.set(false);
    }

    private <R> void compute(
            ResourceLocation systemId,
            UpdatePriority priority,
            boolean supersedable,
            AsyncDataTask<R> task
    ) {
        long startedNanos = System.nanoTime();
        try {
            R result = task.compute();
            long workerNanos = System.nanoTime() - startedNanos;
            if (!accepting.get()) {
                metrics.recordAsyncRejected();
                return;
            }
            CompletedTaskQueue.OfferResult offer = completedTasks.offer(
                    systemId,
                    priority,
                    supersedable,
                    () -> complete(systemId, task, result)
            );
            if (offer.accepted()) {
                metrics.recordAsyncCompleted(workerNanos);
            } else {
                metrics.recordAsyncRejected();
                ModConstants.LOGGER.warn(
                        "[Data Engine] Completed task for {} could not enter the bounded apply queue ({})",
                        systemId,
                        offer
                );
            }
        } catch (Exception exception) {
            metrics.recordFailure(systemId);
            ModConstants.LOGGER.error("[Data Engine] Async calculation failed for {}", systemId, exception);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private static <R> void complete(ResourceLocation systemId, AsyncDataTask<R> task, R result) {
        try {
            if (task.isStillValid(result)) {
                task.apply(result);
            } else {
                task.onDiscarded(result);
            }
        } catch (Exception exception) {
            throw new DataTaskApplicationException(systemId, exception);
        }
    }

    @FunctionalInterface
    interface WorkSubmitter {
        boolean submit(String label, Runnable work);
    }

    /** Preserves the owning system id across server-thread failure isolation. */
    private static final class DataTaskApplicationException extends RuntimeException {
        private DataTaskApplicationException(ResourceLocation systemId, Throwable cause) {
            super("Failed applying async result for " + systemId, cause);
        }
    }
}
