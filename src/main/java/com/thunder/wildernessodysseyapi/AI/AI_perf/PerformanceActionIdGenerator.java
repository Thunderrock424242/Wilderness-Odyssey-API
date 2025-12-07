package com.thunder.wildernessodysseyapi.AI.AI_perf;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates short, human-friendly action identifiers and rollback suffixes.
 */
public class PerformanceActionIdGenerator {
    private int nextNumericId = 1;
    private final Map<String, Integer> rollbackCounters = new HashMap<>();

    public synchronized String nextBaseId() {
        return String.valueOf(nextNumericId++);
    }

    public synchronized String nextRollbackId(String baseId) {
        int suffixIndex = rollbackCounters.getOrDefault(baseId, 0);
        rollbackCounters.put(baseId, suffixIndex + 1);
        char suffix = (char) ('a' + suffixIndex);
        return baseId + suffix;
    }
}
