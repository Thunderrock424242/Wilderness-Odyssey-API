package com.thunder.wildernessodysseyapi.dataengine.queue;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * One server-thread Data Engine action waiting for its processing budget.
 *
 * <p>Coalescible actions represent final state and may replace an older action
 * with the same system, lane, and object key. Event actions are never merged.
 * Instances are mutable only while owned by {@link DataUpdateQueue} so a
 * duplicate submission does not allocate another queue node.</p>
 */
public final class QueuedUpdate {
    private final ResourceLocation systemId;
    private final long objectKey;
    private final UpdateLane lane;
    private final boolean coalescible;

    private UpdatePriority priority;
    private Runnable action;
    private long submittedTick;

    private QueuedUpdate(
            ResourceLocation systemId,
            long objectKey,
            UpdateLane lane,
            UpdatePriority priority,
            boolean coalescible,
            long submittedTick,
            Runnable action
    ) {
        this.systemId = Objects.requireNonNull(systemId, "System id is required");
        this.objectKey = objectKey;
        this.lane = Objects.requireNonNull(lane, "Update lane is required");
        this.priority = Objects.requireNonNull(priority, "Update priority is required");
        this.coalescible = coalescible;
        this.submittedTick = submittedTick;
        this.action = Objects.requireNonNull(action, "Update action is required");
    }

    /** Creates final-state work that supersedes older dirty work for the key. */
    public static QueuedUpdate dirty(
            ResourceLocation systemId,
            long objectKey,
            UpdatePriority priority,
            long submittedTick,
            Runnable action
    ) {
        return new QueuedUpdate(systemId, objectKey, UpdateLane.DIRTY, priority, true, submittedTick, action);
    }

    /** Creates one coalescible scheduled invocation for a registered system. */
    public static QueuedUpdate scheduled(
            ResourceLocation systemId,
            UpdatePriority priority,
            long submittedTick,
            Runnable action
    ) {
        return new QueuedUpdate(systemId, 0L, UpdateLane.SCHEDULED, priority, true, submittedTick, action);
    }

    /** Creates an individual event that must not be coalesced. */
    public static QueuedUpdate event(
            ResourceLocation systemId,
            long eventKey,
            UpdatePriority priority,
            long submittedTick,
            Runnable action
    ) {
        return new QueuedUpdate(systemId, eventKey, UpdateLane.EVENT, priority, false, submittedTick, action);
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

    public boolean coalescible() {
        return coalescible;
    }

    public long submittedTick() {
        return submittedTick;
    }

    /** Runs the action on the logical server thread. */
    public void run() {
        action.run();
    }

    UpdateIdentity identity() {
        return coalescible ? new UpdateIdentity(systemId, objectKey, lane) : null;
    }

    UpdatePriority replaceWith(QueuedUpdate newer) {
        UpdatePriority previousPriority = priority;
        action = newer.action;
        submittedTick = newer.submittedTick;
        if (newer.priority.isMoreUrgentThan(priority)) {
            priority = newer.priority;
        }
        return previousPriority;
    }

    enum UpdateLane {
        DIRTY,
        SCHEDULED,
        EVENT
    }

    record UpdateIdentity(ResourceLocation systemId, long objectKey, UpdateLane lane) {
    }
}
