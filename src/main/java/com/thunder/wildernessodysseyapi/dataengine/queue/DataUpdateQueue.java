package com.thunder.wildernessodysseyapi.dataengine.queue;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * SERVER THREAD ONLY. Bounded multi-priority queue for Data Engine work.
 *
 * <p>Critical work is drained independently from the normal time budget.
 * When the queue is full, supersedable low/background entries are evicted
 * before higher-priority work is rejected. Individual critical events are
 * never silently discarded: a failed submission is reported to the caller.</p>
 */
public final class DataUpdateQueue {
    private final EnumMap<UpdatePriority, ArrayDeque<QueuedUpdate>> queues =
            new EnumMap<>(UpdatePriority.class);
    private final Map<QueuedUpdate.UpdateIdentity, QueuedUpdate> coalescible = new HashMap<>();
    private final int maximumSize;
    private final LongSupplier nanoClock;

    private int size;
    private int peakSize;

    /** Creates a queue using {@link System#nanoTime()} for budget measurement. */
    public DataUpdateQueue(int maximumSize) {
        this(maximumSize, System::nanoTime);
    }

    DataUpdateQueue(int maximumSize, LongSupplier nanoClock) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Maximum queue size must be positive");
        }
        this.maximumSize = maximumSize;
        this.nanoClock = Objects.requireNonNull(nanoClock, "Nano clock is required");
        for (UpdatePriority priority : UpdatePriority.values()) {
            queues.put(priority, new ArrayDeque<>());
        }
    }

    /**
     * Adds or coalesces one update and reports any backpressure decision.
     */
    public SubmissionResult submit(QueuedUpdate update) {
        Objects.requireNonNull(update, "Queued update is required");
        QueuedUpdate.UpdateIdentity identity = update.identity();
        if (identity != null) {
            QueuedUpdate existing = coalescible.get(identity);
            if (existing != null) {
                UpdatePriority previousPriority = existing.replaceWith(update);
                if (existing.priority() != previousPriority) {
                    queues.get(previousPriority).remove(existing);
                    queues.get(existing.priority()).addLast(existing);
                }
                return SubmissionResult.COALESCED;
            }
        }

        boolean evicted = false;
        if (size >= maximumSize) {
            evicted = evictSupersedableLowerThan(update.priority());
            if (!evicted) {
                return update.priority() == UpdatePriority.CRITICAL
                        ? SubmissionResult.REJECTED_CRITICAL
                        : SubmissionResult.DROPPED;
            }
        }

        queues.get(update.priority()).addLast(update);
        if (identity != null) {
            coalescible.put(identity, update);
        }
        size++;
        peakSize = Math.max(peakSize, size);
        return evicted ? SubmissionResult.ACCEPTED_WITH_EVICTION : SubmissionResult.ACCEPTED;
    }

    /** Polls authoritative work that bypasses the background tick budget. */
    public QueuedUpdate pollCritical() {
        return poll(UpdatePriority.CRITICAL);
    }

    /** Polls the next non-critical action in priority order. */
    public QueuedUpdate pollNonCritical() {
        for (UpdatePriority priority : UpdatePriority.values()) {
            if (priority != UpdatePriority.CRITICAL) {
                QueuedUpdate update = poll(priority);
                if (update != null) {
                    return update;
                }
            }
        }
        return null;
    }

    /**
     * Drains critical work, then non-critical work until the monotonic budget is
     * consumed. This method is primarily useful to isolated systems and tests;
     * the root engine uses the poll methods to share one budget across stages.
     */
    public int processAvailable(long budgetNanos, Consumer<QueuedUpdate> processor) {
        Objects.requireNonNull(processor, "Update processor is required");
        int processed = 0;
        QueuedUpdate update;
        while ((update = pollCritical()) != null) {
            processor.accept(update);
            processed++;
        }

        long startNanos = nanoClock.getAsLong();
        while (nanoClock.getAsLong() - startNanos < Math.max(0L, budgetNanos)) {
            update = pollNonCritical();
            if (update == null) {
                break;
            }
            processor.accept(update);
            processed++;
        }
        return processed;
    }

    public int size() {
        return size;
    }

    public int peakSize() {
        return peakSize;
    }

    public int maximumSize() {
        return maximumSize;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Clears all pending work during server shutdown/restart. */
    public void clear() {
        for (ArrayDeque<QueuedUpdate> queue : queues.values()) {
            queue.clear();
        }
        coalescible.clear();
        size = 0;
        peakSize = 0;
    }

    private QueuedUpdate poll(UpdatePriority priority) {
        QueuedUpdate update = queues.get(priority).pollFirst();
        if (update == null) {
            return null;
        }
        size--;
        QueuedUpdate.UpdateIdentity identity = update.identity();
        if (identity != null) {
            coalescible.remove(identity, update);
        }
        return update;
    }

    private boolean evictSupersedableLowerThan(UpdatePriority incomingPriority) {
        for (int index = UpdatePriority.values().length - 1; index > incomingPriority.ordinal(); index--) {
            if (evictOne(UpdatePriority.values()[index])) {
                return true;
            }
        }
        return false;
    }

    private boolean evictOne(UpdatePriority priority) {
        ArrayDeque<QueuedUpdate> queue = queues.get(priority);
        Iterator<QueuedUpdate> iterator = queue.descendingIterator();
        while (iterator.hasNext()) {
            QueuedUpdate candidate = iterator.next();
            if (!candidate.coalescible()) {
                continue;
            }
            iterator.remove();
            coalescible.remove(candidate.identity(), candidate);
            size--;
            return true;
        }
        return false;
    }

    /** Outcome used by callers to update metrics and handle explicit rejection. */
    public enum SubmissionResult {
        ACCEPTED,
        COALESCED,
        ACCEPTED_WITH_EVICTION,
        DROPPED,
        REJECTED_CRITICAL;

        public boolean accepted() {
            return this == ACCEPTED || this == COALESCED || this == ACCEPTED_WITH_EVICTION;
        }

        public boolean activatedBackpressure() {
            return this == ACCEPTED_WITH_EVICTION || this == DROPPED || this == REJECTED_CRITICAL;
        }
    }
}
