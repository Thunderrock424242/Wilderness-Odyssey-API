package com.thunder.wildernessodysseyapi.performance.tickengine;

/**
 * Mutable allocation-free budget reused by the Tick Engine each server tick.
 */
public final class TickBudget {
    // Integrated-client debug rendering reads these server-thread values. Volatile
    // publication avoids locking either thread while keeping diagnostics coherent enough.
    private volatile long tickStartNanos;
    private volatile long optionalStartNanos;
    private volatile long deadlineNanos;
    private volatile long allowedWorkNanos;
    private volatile long usedWorkNanos;

    void begin(long tickStartNanos, long optionalStartNanos, long allowedWorkNanos) {
        this.tickStartNanos = tickStartNanos;
        this.optionalStartNanos = optionalStartNanos;
        this.allowedWorkNanos = Math.max(0L, allowedWorkNanos);
        this.deadlineNanos = saturatedAdd(optionalStartNanos, this.allowedWorkNanos);
        this.usedWorkNanos = 0L;
    }

    void finish(long endNanos) {
        usedWorkNanos = Math.max(0L, endNanos - optionalStartNanos);
    }

    public long remainingNanos(long nowNanos) {
        return Math.max(0L, deadlineNanos - nowNanos);
    }

    public long deadlineNanos() {
        return deadlineNanos;
    }

    public long allowedWorkNanos() {
        return allowedWorkNanos;
    }

    public long usedWorkNanos() {
        return usedWorkNanos;
    }

    /** Returns unused optional capacity captured when the tick's WO work finished. */
    public long unusedWorkNanos() {
        return Math.max(0L, allowedWorkNanos - usedWorkNanos);
    }

    public long baseTickWorkNanos() {
        return Math.max(0L, optionalStartNanos - tickStartNanos);
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
