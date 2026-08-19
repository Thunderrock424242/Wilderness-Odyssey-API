package com.thunder.wildernessodysseyapi.dataengine.dirty;

import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Active final-state change waiting to be converted into queued work.
 *
 * <p>The tracker mutates an existing entry when the same key is marked again,
 * avoiding duplicate active nodes while retaining the newest reason/tick and
 * most urgent priority.</p>
 */
public final class DirtyEntry {
    private final ResourceLocation systemId;
    private final long objectKey;

    private UpdatePriority priority;
    private String reason;
    private long markedTick;
    private int markCount;

    DirtyEntry(ResourceLocation systemId, long objectKey, UpdatePriority priority, String reason, long markedTick) {
        this.systemId = Objects.requireNonNull(systemId, "System id is required");
        this.objectKey = objectKey;
        this.priority = Objects.requireNonNull(priority, "Priority is required");
        this.reason = reason;
        this.markedTick = markedTick;
        this.markCount = 1;
    }

    public ResourceLocation systemId() {
        return systemId;
    }

    public long objectKey() {
        return objectKey;
    }

    public UpdatePriority priority() {
        return priority;
    }

    public String reason() {
        return reason;
    }

    public long markedTick() {
        return markedTick;
    }

    public int markCount() {
        return markCount;
    }

    void merge(UpdatePriority newPriority, String newReason, long newMarkedTick) {
        if (newPriority.isMoreUrgentThan(priority)) {
            priority = newPriority;
        }
        if (newReason != null && !newReason.isBlank()) {
            reason = newReason;
        }
        markedTick = Math.max(markedTick, newMarkedTick);
        markCount++;
    }

    DirtyEntry copy() {
        DirtyEntry copy = new DirtyEntry(systemId, objectKey, priority, reason, markedTick);
        copy.markCount = markCount;
        return copy;
    }

    DirtyKey key() {
        return new DirtyKey(systemId, objectKey);
    }

    record DirtyKey(ResourceLocation systemId, long objectKey) {
    }
}
