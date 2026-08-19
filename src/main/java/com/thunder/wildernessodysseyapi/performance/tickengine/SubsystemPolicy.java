package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.Locale;
import java.util.Objects;

/**
 * Registration metadata for one opt-in Wilderness Odyssey subsystem.
 *
 * @param maximumIntervalTicks longest allowed interval when suspension is forbidden
 */
public record SubsystemPolicy(
        String id,
        String displayName,
        TickPriority importance,
        int maximumIntervalTicks,
        boolean suspensionAllowed
) {
    public SubsystemPolicy {
        id = normalizeId(id);
        displayName = Objects.requireNonNullElse(displayName, id);
        importance = Objects.requireNonNull(importance, "importance");
        maximumIntervalTicks = Math.max(1, maximumIntervalTicks);
    }

    private static String normalizeId(String value) {
        String normalized = Objects.requireNonNullElse(value, "unknown").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Subsystem ID cannot be blank");
        }
        return normalized;
    }
}
