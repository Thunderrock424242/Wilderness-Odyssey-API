package com.thunder.wildernessodysseyapi.performance.coordinator;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/** Borrows existing executors for explicitly disposable work. Closing never waits or touches required jobs. */
public final class DisposableWorkTracker {
    private final int capacity;
    private final Set<FutureTask<Void>> tasks = new HashSet<>();
    private final AtomicLong completedCost = new AtomicLong();
    private long rejected;
    private long cancelled;
    private boolean stopped;

    public DisposableWorkTracker(int capacity) { this.capacity = Math.max(1, capacity); }

    public boolean submit(Executor executor, Runnable work, boolean allowed) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            long start = System.nanoTime();
            try { work.run(); }
            catch (RuntimeException failure) { ModConstants.LOGGER.warn("[WO Performance] Disposable work failed ({})", failure.getClass().getSimpleName()); }
            finally { completedCost.addAndGet(Math.max(0, System.nanoTime() - start)); }
        }, null) {
            @Override protected void done() { synchronized (DisposableWorkTracker.this) { tasks.remove(this); } }
        };
        synchronized (this) {
            if (stopped || !allowed || tasks.size() >= capacity) { rejected++; return false; }
            tasks.add(task);
            // Admission and dispatch are atomic with stop: no late task escapes cancellation.
            try { executor.execute(task); return true; }
            catch (RejectedExecutionException failure) { tasks.remove(task); rejected++; return false; }
        }
    }

    public void stop() {
        List<FutureTask<Void>> pending;
        synchronized (this) { stopped = true; pending = List.copyOf(tasks); }
        for (FutureTask<Void> task : pending) {
            if (task.cancel(true)) synchronized (this) { cancelled++; }
        }
    }

    public synchronized Snapshot snapshot() { return new Snapshot(tasks.size(), rejected, cancelled, completedCost.get()); }
    public record Snapshot(int inFlight, long rejected, long cancelled, long completedCostNanos) { }
}
