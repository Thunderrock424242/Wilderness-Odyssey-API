package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.Locale;
import java.util.Objects;

/**
 * One bounded unit of tick-aware Wilderness Odyssey-owned work.
 */
public record TickTask(
        String subsystem,
        TickPriority priority,
        long creationTick,
        long staleAfterTicks,
        String coalescingKey,
        String context,
        WorkStep work
) {
    public TickTask {
        subsystem = normalize(subsystem);
        priority = Objects.requireNonNull(priority, "priority");
        staleAfterTicks = Math.max(0L, staleAfterTicks);
        coalescingKey = Objects.requireNonNullElse(coalescingKey, "");
        context = Objects.requireNonNullElse(context, "");
        work = Objects.requireNonNull(work, "work");
    }

    /** Creates a one-step task without coalescing. */
    public static TickTask once(String subsystem, TickPriority priority, long creationTick, Runnable work) {
        Objects.requireNonNull(work, "work");
        return new TickTask(subsystem, priority, creationTick, 0L, "", "", () -> {
            work.run();
            return Result.COMPLETE;
        });
    }

    /** Returns true when this task may no longer be useful. Zero means no expiry. */
    public boolean isStale(long currentTick) {
        return staleAfterTicks > 0L && currentTick >= creationTick
                && currentTick - creationTick > staleAfterTicks;
    }

    String coalescingIdentity() {
        return coalescingKey.isEmpty() ? "" : subsystem + ':' + coalescingKey;
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNullElse(value, "unknown").trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    public enum Result {
        COMPLETE,
        DEFER
    }

    /** Performs one bounded server-thread step. */
    @FunctionalInterface
    public interface WorkStep {
        Result run() throws Exception;
    }
}
