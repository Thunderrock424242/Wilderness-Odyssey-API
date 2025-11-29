package com.thunder.wildernessodysseyapi.AI_perf;

/**
 * Lifecycle states for a performance action that started as AI advice
 * and requires human approval before execution.
 */
public enum PerformanceActionStatus {
    PENDING,
    APPROVED,
    APPLIED,
    REJECTED,
    EXPIRED
}
