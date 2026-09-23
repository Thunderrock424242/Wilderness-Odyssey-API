package com.thunder.aether.server.api;

/** Bounded, service-wide request admission for the public Aether gateway. */
public final class RequestLimits {
    private final int limit;
    private long windowStart;
    private int count;

    /** Sets the maximum number of generation requests admitted in one minute. */
    public RequestLimits(int limit) {
        if (limit < 1) { throw new IllegalArgumentException("Invalid request limit"); }
        this.limit = limit;
    }

    /** All callers share one budget; supplied player/server IDs and headers cannot reset it. */
    public synchronized boolean allow(long nowNanos) {
        if (count == 0 || nowNanos - windowStart >= 60_000_000_000L) {
            windowStart = nowNanos;
            count = 0;
        }
        if (count >= limit) { return false; }
        count++;
        return true;
    }

    /** Bounds opt-in log content and removes control characters. */
    public String safeLog(String value) {
        String safe = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(2000, safe.length()));
    }
}
