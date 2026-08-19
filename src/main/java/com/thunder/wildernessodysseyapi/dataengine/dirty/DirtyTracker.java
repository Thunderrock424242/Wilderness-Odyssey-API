package com.thunder.wildernessodysseyapi.dataengine.dirty;

import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SERVER THREAD ONLY. Push-based bounded dirty-state tracker.
 *
 * <p>Consumers poll only active entries; no registered-object scan is needed.
 * Duplicate marks update the existing node in place. At capacity, a critical
 * mark may replace a supersedable low/background mark, otherwise rejection is
 * explicit so the authoritative owner can retain its own dirty bit.</p>
 */
public final class DirtyTracker {
    private final Map<DirtyEntry.DirtyKey, DirtyEntry> entries = new LinkedHashMap<>();
    private final EnumMap<UpdatePriority, ArrayDeque<DirtyEntry>> active = new EnumMap<>(UpdatePriority.class);
    private final int maximumEntries;

    public DirtyTracker(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("Maximum dirty entries must be positive");
        }
        this.maximumEntries = maximumEntries;
        for (UpdatePriority priority : UpdatePriority.values()) {
            active.put(priority, new ArrayDeque<>());
        }
    }

    /** Marks a key active without adding a duplicate active node. */
    public MarkResult markDirty(
            ResourceLocation systemId,
            long objectKey,
            UpdatePriority priority,
            String reason,
            long markedTick
    ) {
        Objects.requireNonNull(systemId, "System id is required");
        Objects.requireNonNull(priority, "Priority is required");
        DirtyEntry.DirtyKey key = new DirtyEntry.DirtyKey(systemId, objectKey);
        DirtyEntry existing = entries.get(key);
        if (existing != null) {
            UpdatePriority previousPriority = existing.priority();
            existing.merge(priority, reason, markedTick);
            if (existing.priority() != previousPriority) {
                active.get(previousPriority).remove(existing);
                active.get(existing.priority()).addLast(existing);
            }
            return MarkResult.COALESCED;
        }

        boolean evicted = false;
        if (entries.size() >= maximumEntries) {
            evicted = evictLowerPriority(priority);
            if (!evicted) {
                return priority == UpdatePriority.CRITICAL
                        ? MarkResult.REJECTED_CRITICAL
                        : MarkResult.DROPPED;
            }
        }

        DirtyEntry entry = new DirtyEntry(systemId, objectKey, priority, reason, markedTick);
        entries.put(key, entry);
        active.get(priority).addLast(entry);
        return evicted ? MarkResult.ACCEPTED_WITH_EVICTION : MarkResult.ACCEPTED;
    }

    public boolean isDirty(ResourceLocation systemId, long objectKey) {
        return entries.containsKey(new DirtyEntry.DirtyKey(systemId, objectKey));
    }

    /** Clears a key explicitly, normally after an owner no longer needs work. */
    public boolean clearDirty(ResourceLocation systemId, long objectKey) {
        DirtyEntry removed = entries.remove(new DirtyEntry.DirtyKey(systemId, objectKey));
        return removed != null && active.get(removed.priority()).remove(removed);
    }

    /** Retrieves and removes the oldest critical dirty entry. */
    public DirtyEntry pollCritical() {
        return poll(UpdatePriority.CRITICAL);
    }

    /** Retrieves and removes the next non-critical entry in priority order. */
    public DirtyEntry pollNonCritical() {
        for (UpdatePriority priority : UpdatePriority.values()) {
            if (priority == UpdatePriority.CRITICAL) {
                continue;
            }
            DirtyEntry entry = poll(priority);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private DirtyEntry poll(UpdatePriority priority) {
        DirtyEntry entry = active.get(priority).pollFirst();
        if (entry != null) {
            entries.remove(entry.key(), entry);
        }
        return entry;
    }

    /** Restores a polled entry when the downstream bounded queue rejects it. */
    public void requeue(DirtyEntry entry) {
        Objects.requireNonNull(entry, "Dirty entry is required");
        DirtyEntry existing = entries.get(entry.key());
        if (existing != null) {
            existing.merge(entry.priority(), entry.reason(), entry.markedTick());
            return;
        }
        entries.put(entry.key(), entry);
        active.get(entry.priority()).addFirst(entry);
    }

    /** Returns diagnostic copies so callers cannot mutate tracker-owned nodes. */
    public List<DirtyEntry> dirtyEntries() {
        List<DirtyEntry> snapshot = new ArrayList<>(entries.size());
        for (UpdatePriority priority : UpdatePriority.values()) {
            for (DirtyEntry entry : active.get(priority)) {
                snapshot.add(entry.copy());
            }
        }
        return List.copyOf(snapshot);
    }

    public int size() {
        return entries.size();
    }

    public int maximumEntries() {
        return maximumEntries;
    }

    public void clear() {
        entries.clear();
        for (ArrayDeque<DirtyEntry> queue : active.values()) {
            queue.clear();
        }
    }

    private boolean evictLowerPriority(UpdatePriority incomingPriority) {
        for (int index = UpdatePriority.values().length - 1; index > incomingPriority.ordinal(); index--) {
            UpdatePriority candidatePriority = UpdatePriority.values()[index];
            Iterator<DirtyEntry> iterator = active.get(candidatePriority).descendingIterator();
            if (iterator.hasNext()) {
                DirtyEntry entry = iterator.next();
                iterator.remove();
                entries.remove(entry.key(), entry);
                return true;
            }
        }
        return false;
    }

    /** Outcome used to make all capacity drops visible to callers and metrics. */
    public enum MarkResult {
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
