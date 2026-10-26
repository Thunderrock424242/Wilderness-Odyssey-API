package com.thunder.wildernessodysseyapi.playtest.diagnostics;

/** Monotonic authorization fence, also read by logging threads during a blocked client tick. */
public final class DiagnosticLease {
    private long generation;
    private long expiresAt;
    private DiagnosticProtocol.Policy policy;

    public synchronized long generation() { return generation; }

    public synchronized boolean accept(long expectedGeneration, DiagnosticProtocol.Policy next, long now) {
        if (expectedGeneration != generation) return false;
        if (!next.enabled() || next.leaseSeconds() < 1 || (policy != null
                && (!policy.campaignId().equals(next.campaignId()) || !policy.testerId().equals(next.testerId())))) {
            clear();
            return false;
        }
        policy = next;
        expiresAt = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(Math.min(60, next.leaseSeconds()));
        return true;
    }

    public synchronized boolean active(long now) { return policy != null && now - expiresAt < 0; }
    public synchronized DiagnosticProtocol.Policy policy() { return policy; }
    public synchronized void clear() { policy = null; expiresAt = 0; }
    public synchronized void stop() { generation++; clear(); }
}
