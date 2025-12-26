package com.thunder.wildernessodysseyapi.async;

/**
 * Immutable snapshot of the async task system's state for diagnostics.
 */
public record AsyncTaskStats(
        boolean enabled,
        int configuredThreads,
        int queueCapacity,
        int activeCpuWorkers,
        int queuedWorkerTasks,
        int mainThreadBacklog,
        int appliedLastTick,
        int rejectedTasks,
        int callerRunsEvents
) { }
