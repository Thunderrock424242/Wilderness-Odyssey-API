package com.thunder.wildernessodysseyapi.dataengine.network;

import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;

/**
 * IMMUTABLE compact encoded field delta for one system-owned object/region.
 *
 * <p>The changed-field mask is explicitly defined by the owning system. Two
 * pending deltas coalesce only when system, target, and mask are identical, so
 * replacing the body with the latest value cannot lose unrelated fields.</p>
 */
public final class DataDelta {
    public static final int MAXIMUM_BODY_BYTES = 262_144;

    private final ResourceLocation systemId;
    private final long targetKey;
    private final long changedFields;
    private final UpdatePriority priority;
    private final byte[] body;

    public DataDelta(
            ResourceLocation systemId,
            long targetKey,
            long changedFields,
            UpdatePriority priority,
            byte[] body
    ) {
        this.systemId = Objects.requireNonNull(systemId, "Delta system id is required");
        if (changedFields == 0L) {
            throw new IllegalArgumentException("Delta changed-field mask cannot be empty");
        }
        this.targetKey = targetKey;
        this.changedFields = changedFields;
        this.priority = Objects.requireNonNull(priority, "Delta priority is required");
        this.body = body == null ? new byte[0] : body.clone();
        if (this.body.length > MAXIMUM_BODY_BYTES) {
            throw new IllegalArgumentException("Delta body exceeds the hard network safety bound");
        }
    }

    public ResourceLocation systemId() {
        return systemId;
    }

    public long targetKey() {
        return targetKey;
    }

    public long changedFields() {
        return changedFields;
    }

    public UpdatePriority priority() {
        return priority;
    }

    public byte[] body() {
        return body.clone();
    }

    byte[] bodyUnsafe() {
        return body;
    }

    public int approximateEncodedBytes() {
        return 32 + systemId.toString().length() + body.length;
    }

    DeltaIdentity identity() {
        return new DeltaIdentity(systemId, targetKey, changedFields);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DataDelta other)) {
            return false;
        }
        return targetKey == other.targetKey
                && changedFields == other.changedFields
                && systemId.equals(other.systemId)
                && priority == other.priority
                && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(systemId, targetKey, changedFields, priority);
        return 31 * result + Arrays.hashCode(body);
    }

    record DeltaIdentity(ResourceLocation systemId, long targetKey, long changedFields) {
    }
}
