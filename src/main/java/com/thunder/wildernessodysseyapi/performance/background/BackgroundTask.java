package com.thunder.wildernessodysseyapi.performance.background;

import java.util.Objects;

/**
 * Describes one bounded unit of Wilderness Odyssey-owned background work.
 *
 * <p>Callbacks run on the logical server thread. A callback that cannot finish
 * its whole operation cheaply should retain its own cursor and return
 * {@link Result#DEFER}; the scheduler will offer it again on a later pass.</p>
 */
public record BackgroundTask(
        String subsystem,
        WorkPriority priority,
        long estimatedCostNanos,
        long creationTick,
        String context,
        WorkStep work
) {
    public BackgroundTask {
        subsystem = normalizeSubsystem(subsystem);
        priority = Objects.requireNonNull(priority, "priority");
        estimatedCostNanos = Math.max(0L, estimatedCostNanos);
        context = Objects.requireNonNullElse(context, "");
        work = Objects.requireNonNull(work, "work");
    }

    /** Creates a task whose callback is expected to complete in one invocation. */
    public static BackgroundTask once(
            String subsystem,
            WorkPriority priority,
            long creationTick,
            Runnable work
    ) {
        Objects.requireNonNull(work, "work");
        return new BackgroundTask(subsystem, priority, 0L, creationTick, "", () -> {
            work.run();
            return Result.COMPLETE;
        });
    }

    private static String normalizeSubsystem(String subsystem) {
        String normalized = Objects.requireNonNullElse(subsystem, "unknown").trim()
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    /** Result returned by a cooperative task step. */
    public enum Result {
        COMPLETE,
        DEFER
    }

    /** Performs one bounded server-thread step of a background operation. */
    @FunctionalInterface
    public interface WorkStep {
        Result run() throws Exception;
    }
}
