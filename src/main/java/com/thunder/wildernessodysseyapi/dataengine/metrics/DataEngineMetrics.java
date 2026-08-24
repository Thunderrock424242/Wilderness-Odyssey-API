package com.thunder.wildernessodysseyapi.dataengine.metrics;

import com.thunder.wildernessodysseyapi.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.dataengine.queue.DataUpdateQueue;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * THREAD SAFE. Allocation-light counters and gauges for evaluating the engine.
 *
 * <p>Hot paths update {@link LongAdder}s and atomics. Map/list snapshots are
 * created only when a command, debug page, or other diagnostic consumer asks.</p>
 */
public final class DataEngineMetrics {
    private final LongAdder updatesSubmitted = new LongAdder();
    private final LongAdder updatesProcessed = new LongAdder();
    private final LongAdder updatesCoalesced = new LongAdder();
    private final LongAdder updateFailures = new LongAdder();
    private final LongAdder networkBatches = new LongAdder();
    private final LongAdder networkEntries = new LongAdder();
    private final LongAdder estimatedBytesSent = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder asyncTasksSubmitted = new LongAdder();
    private final LongAdder asyncTasksCompleted = new LongAdder();
    private final LongAdder asyncTasksRejected = new LongAdder();
    private final LongAdder totalMainThreadProcessingNanos = new LongAdder();
    private final LongAdder totalWorkerProcessingNanos = new LongAdder();
    private final LongAdder interestFilteredUpdates = new LongAdder();
    private final LongAdder droppedOrSupersededBackgroundUpdates = new LongAdder();
    private final LongAdder backpressureEvents = new LongAdder();

    private final AtomicLong dirtyEntries = new AtomicLong();
    private final AtomicLong queuedWork = new AtomicLong();
    private final AtomicLong queuePeak = new AtomicLong();
    private final AtomicLong cacheEntries = new AtomicLong();
    private final AtomicLong asyncQueueLength = new AtomicLong();
    private final AtomicLong lastMainThreadProcessingNanos = new AtomicLong();
    private final AtomicLong processedPerSecond = new AtomicLong();
    private final AtomicLong coalescedPerSecond = new AtomicLong();
    private final AtomicBoolean backpressureActive = new AtomicBoolean();
    private final Map<ResourceLocation, SystemCounters> systems = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private long lastRateSampleNanos;
    private long lastProcessedSample;
    private long lastCoalescedSample;

    public DataEngineMetrics(boolean enabled) {
        this.enabled = enabled;
    }

    /** Enables or disables counter collection after a server-config reload. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void registerSystem(ResourceLocation systemId) {
        systems.computeIfAbsent(systemId, ignored -> new SystemCounters());
    }

    /** Clears all counters, gauges, and subsystem registrations for a fresh server lifecycle. */
    public void beginServerLifecycle(boolean enabled) {
        this.enabled = enabled;
        resetCounters();
        systems.clear();
        dirtyEntries.set(0L);
        queuedWork.set(0L);
        queuePeak.set(0L);
        cacheEntries.set(0L);
        asyncQueueLength.set(0L);
        lastMainThreadProcessingNanos.set(0L);
        backpressureActive.set(false);
    }

    public void recordSubmission(ResourceLocation systemId, DataUpdateQueue.SubmissionResult result) {
        if (!enabled) {
            return;
        }
        if (result.accepted()) {
            updatesSubmitted.increment();
            systems.computeIfAbsent(systemId, ignored -> new SystemCounters()).submitted.increment();
        }
        if (result == DataUpdateQueue.SubmissionResult.COALESCED) {
            updatesCoalesced.increment();
        }
        if (result == DataUpdateQueue.SubmissionResult.ACCEPTED_WITH_EVICTION
                || result == DataUpdateQueue.SubmissionResult.DROPPED) {
            droppedOrSupersededBackgroundUpdates.increment();
        }
        if (result.activatedBackpressure()) {
            recordBackpressure();
        }
    }

    public void recordProcessed(ResourceLocation systemId, long processingNanos) {
        if (!enabled) {
            return;
        }
        updatesProcessed.increment();
        SystemCounters counters = systems.computeIfAbsent(systemId, ignored -> new SystemCounters());
        counters.processed.increment();
        counters.processingNanos.add(Math.max(0L, processingNanos));
    }

    public void recordFailure(ResourceLocation systemId) {
        if (!enabled) {
            return;
        }
        updateFailures.increment();
        systems.computeIfAbsent(systemId, ignored -> new SystemCounters()).failures.increment();
    }

    public void recordNetworkBatch(int entries, long estimatedBytes) {
        if (!enabled) {
            return;
        }
        networkBatches.increment();
        networkEntries.add(Math.max(0, entries));
        estimatedBytesSent.add(Math.max(0L, estimatedBytes));
    }

    /** Records one real packet batch and attributes its entries to their owning systems. */
    public void recordNetworkBatch(List<DataDelta> deltas, long estimatedBytes) {
        if (!enabled) {
            return;
        }
        List<DataDelta> entries = List.copyOf(deltas);
        recordNetworkBatch(entries.size(), estimatedBytes);
        Set<ResourceLocation> systemsInBatch = new HashSet<>();
        for (DataDelta delta : entries) {
            SystemCounters counters = systems.computeIfAbsent(
                    delta.systemId(),
                    ignored -> new SystemCounters()
            );
            counters.networkEntries.increment();
            counters.estimatedNetworkBytes.add(delta.approximateEncodedBytes());
            if (systemsInBatch.add(delta.systemId())) {
                counters.networkBatches.increment();
            }
        }
    }

    /** Records replacement of an older pending network delta by newer final state. */
    public void recordNetworkCoalesced() {
        if (enabled) {
            updatesCoalesced.increment();
        }
    }

    /** Records a bounded-queue eviction or rejection outside the main work queue. */
    public void recordDroppedOrSuperseded() {
        if (enabled) {
            droppedOrSupersededBackgroundUpdates.increment();
            recordBackpressure();
        }
    }

    public void recordCacheHit() {
        if (enabled) {
            cacheHits.increment();
        }
    }

    public void recordCacheMiss() {
        if (enabled) {
            cacheMisses.increment();
        }
    }

    public void recordAsyncSubmitted() {
        if (enabled) {
            asyncTasksSubmitted.increment();
        }
    }

    public void recordAsyncCompleted(long workerNanos) {
        if (enabled) {
            asyncTasksCompleted.increment();
            totalWorkerProcessingNanos.add(Math.max(0L, workerNanos));
        }
    }

    public void recordAsyncRejected() {
        if (enabled) {
            asyncTasksRejected.increment();
            recordBackpressure();
        }
    }

    public void recordInterestFiltered(long count) {
        if (enabled && count > 0L) {
            interestFilteredUpdates.add(count);
        }
    }

    public void recordMainThreadProcessing(long elapsedNanos) {
        long bounded = Math.max(0L, elapsedNanos);
        lastMainThreadProcessingNanos.set(bounded);
        if (enabled) {
            totalMainThreadProcessingNanos.add(bounded);
        }
    }

    public void updateGauges(
            int dirtyCount,
            int queuedCount,
            int peakCount,
            int cacheCount,
            int asyncQueueCount,
            boolean underBackpressure
    ) {
        dirtyEntries.set(Math.max(0, dirtyCount));
        queuedWork.set(Math.max(0, queuedCount));
        queuePeak.accumulateAndGet(Math.max(0, peakCount), Math::max);
        cacheEntries.set(Math.max(0, cacheCount));
        asyncQueueLength.set(Math.max(0, asyncQueueCount));
        backpressureActive.set(underBackpressure);
    }

    /** Updates one-second rates using monotonic elapsed time rather than assumed TPS. */
    public void sampleRates(long nowNanos) {
        if (!enabled) {
            processedPerSecond.set(0L);
            coalescedPerSecond.set(0L);
            return;
        }
        if (lastRateSampleNanos == 0L) {
            lastRateSampleNanos = nowNanos;
            lastProcessedSample = updatesProcessed.sum();
            lastCoalescedSample = updatesCoalesced.sum();
            return;
        }
        long elapsed = nowNanos - lastRateSampleNanos;
        if (elapsed < 1_000_000_000L) {
            return;
        }
        long processed = updatesProcessed.sum();
        long coalesced = updatesCoalesced.sum();
        processedPerSecond.set(rate(processed - lastProcessedSample, elapsed));
        coalescedPerSecond.set(rate(coalesced - lastCoalescedSample, elapsed));
        lastRateSampleNanos = nowNanos;
        lastProcessedSample = processed;
        lastCoalescedSample = coalesced;
    }

    public DataEngineMetricsSnapshot snapshot() {
        Map<ResourceLocation, DataSystemMetricsSnapshot> systemSnapshots = new HashMap<>();
        for (Map.Entry<ResourceLocation, SystemCounters> entry : systems.entrySet()) {
            SystemCounters counters = entry.getValue();
            systemSnapshots.put(entry.getKey(), new DataSystemMetricsSnapshot(
                    entry.getKey(),
                    counters.submitted.sum(),
                    counters.processed.sum(),
                    counters.failures.sum(),
                    counters.processingNanos.sum(),
                    counters.networkBatches.sum(),
                    counters.networkEntries.sum(),
                    counters.estimatedNetworkBytes.sum()
            ));
        }
        return new DataEngineMetricsSnapshot(
                enabled,
                updatesSubmitted.sum(),
                updatesProcessed.sum(),
                updatesCoalesced.sum(),
                updateFailures.sum(),
                processedPerSecond.get(),
                coalescedPerSecond.get(),
                dirtyEntries.get(),
                queuedWork.get(),
                queuePeak.get(),
                networkBatches.sum(),
                networkEntries.sum(),
                estimatedBytesSent.sum(),
                cacheHits.sum(),
                cacheMisses.sum(),
                cacheEntries.get(),
                asyncTasksSubmitted.sum(),
                asyncTasksCompleted.sum(),
                asyncTasksRejected.sum(),
                asyncQueueLength.get(),
                lastMainThreadProcessingNanos.get(),
                totalMainThreadProcessingNanos.sum(),
                totalWorkerProcessingNanos.sum(),
                interestFilteredUpdates.sum(),
                droppedOrSupersededBackgroundUpdates.sum(),
                backpressureEvents.sum(),
                backpressureActive.get(),
                Map.copyOf(systemSnapshots)
        );
    }

    /** Resets totals/rates while preserving current queue/cache gauges. */
    public void resetCounters() {
        updatesSubmitted.reset();
        updatesProcessed.reset();
        updatesCoalesced.reset();
        updateFailures.reset();
        networkBatches.reset();
        networkEntries.reset();
        estimatedBytesSent.reset();
        cacheHits.reset();
        cacheMisses.reset();
        asyncTasksSubmitted.reset();
        asyncTasksCompleted.reset();
        asyncTasksRejected.reset();
        totalMainThreadProcessingNanos.reset();
        totalWorkerProcessingNanos.reset();
        interestFilteredUpdates.reset();
        droppedOrSupersededBackgroundUpdates.reset();
        backpressureEvents.reset();
        processedPerSecond.set(0L);
        coalescedPerSecond.set(0L);
        lastRateSampleNanos = 0L;
        lastProcessedSample = 0L;
        lastCoalescedSample = 0L;
        for (SystemCounters counters : systems.values()) {
            counters.reset();
        }
    }

    private void recordBackpressure() {
        backpressureEvents.increment();
        backpressureActive.set(true);
    }

    private static long rate(long count, long elapsedNanos) {
        if (count <= 0L || elapsedNanos <= 0L) {
            return 0L;
        }
        return Math.round(count * 1_000_000_000.0D / elapsedNanos);
    }

    private static final class SystemCounters {
        private final LongAdder submitted = new LongAdder();
        private final LongAdder processed = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder processingNanos = new LongAdder();
        private final LongAdder networkBatches = new LongAdder();
        private final LongAdder networkEntries = new LongAdder();
        private final LongAdder estimatedNetworkBytes = new LongAdder();

        private void reset() {
            submitted.reset();
            processed.reset();
            failures.reset();
            processingNanos.reset();
            networkBatches.reset();
            networkEntries.reset();
            estimatedNetworkBytes.reset();
        }
    }
}
