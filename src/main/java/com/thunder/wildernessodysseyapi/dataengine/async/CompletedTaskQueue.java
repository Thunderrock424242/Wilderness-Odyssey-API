package com.thunder.wildernessodysseyapi.dataengine.async;

import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Objects;

/**
 * THREAD SAFE producer / SERVER THREAD consumer queue for completed worker work.
 *
 * <p>The queue is bounded. Critical completions first evict supersedable
 * low/background completions; if no safe eviction exists, rejection is explicit.</p>
 */
public final class CompletedTaskQueue {
    private final EnumMap<UpdatePriority, ArrayDeque<CompletedTask>> queues =
            new EnumMap<>(UpdatePriority.class);
    private final int maximumSize;
    private int size;

    public CompletedTaskQueue(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Maximum completed-task queue size must be positive");
        }
        this.maximumSize = maximumSize;
        for (UpdatePriority priority : UpdatePriority.values()) {
            queues.put(priority, new ArrayDeque<>());
        }
    }

    /** Offers one server-thread completion from a worker thread. */
    synchronized OfferResult offer(
            ResourceLocation systemId,
            UpdatePriority priority,
            boolean supersedable,
            Runnable completion
    ) {
        Objects.requireNonNull(systemId, "System id is required");
        Objects.requireNonNull(priority, "Completion priority is required");
        Objects.requireNonNull(completion, "Completion action is required");
        boolean evicted = false;
        if (size >= maximumSize) {
            evicted = priority == UpdatePriority.CRITICAL && evictLowPriority();
            if (!evicted) {
                return priority == UpdatePriority.CRITICAL ? OfferResult.REJECTED_CRITICAL : OfferResult.DROPPED;
            }
        }
        queues.get(priority).addLast(new CompletedTask(systemId, priority, supersedable, completion));
        size++;
        return evicted ? OfferResult.ACCEPTED_WITH_EVICTION : OfferResult.ACCEPTED;
    }

    /** SERVER THREAD ONLY. Polls critical completions outside the normal budget. */
    public synchronized CompletedTask pollCritical() {
        return poll(UpdatePriority.CRITICAL);
    }

    /** SERVER THREAD ONLY. Polls the next non-critical completion. */
    public synchronized CompletedTask pollNonCritical() {
        for (UpdatePriority priority : UpdatePriority.values()) {
            if (priority == UpdatePriority.CRITICAL) {
                continue;
            }
            CompletedTask task = poll(priority);
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized void clear() {
        for (ArrayDeque<CompletedTask> queue : queues.values()) {
            queue.clear();
        }
        size = 0;
    }

    private CompletedTask poll(UpdatePriority priority) {
        CompletedTask task = queues.get(priority).pollFirst();
        if (task != null) {
            size--;
        }
        return task;
    }

    private boolean evictLowPriority() {
        return evictOne(UpdatePriority.BACKGROUND) || evictOne(UpdatePriority.LOW);
    }

    private boolean evictOne(UpdatePriority priority) {
        Iterator<CompletedTask> iterator = queues.get(priority).descendingIterator();
        while (iterator.hasNext()) {
            CompletedTask task = iterator.next();
            if (!task.supersedable) {
                continue;
            }
            iterator.remove();
            size--;
            return true;
        }
        return false;
    }

    /** One validated/apply action now owned by the server thread. */
    public static final class CompletedTask {
        private final ResourceLocation systemId;
        private final UpdatePriority priority;
        private final boolean supersedable;
        private final Runnable completion;

        private CompletedTask(
                ResourceLocation systemId,
                UpdatePriority priority,
                boolean supersedable,
                Runnable completion
        ) {
            this.systemId = systemId;
            this.priority = priority;
            this.supersedable = supersedable;
            this.completion = completion;
        }

        public ResourceLocation systemId() {
            return systemId;
        }

        public UpdatePriority priority() {
            return priority;
        }

        /** Runs validation followed by application on the server thread. */
        public void run() {
            completion.run();
        }
    }

    enum OfferResult {
        ACCEPTED,
        ACCEPTED_WITH_EVICTION,
        DROPPED,
        REJECTED_CRITICAL;

        boolean accepted() {
            return this == ACCEPTED || this == ACCEPTED_WITH_EVICTION;
        }
    }
}
