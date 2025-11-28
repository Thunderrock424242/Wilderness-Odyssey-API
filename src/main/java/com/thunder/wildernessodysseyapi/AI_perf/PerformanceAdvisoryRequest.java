package com.thunder.wildernessodysseyapi.AI_perf;

import java.util.List;

/**
 * Immutable snapshot of expensive server activity that can be handed to an AI helper
 * for advisory recommendations.
 */
public record PerformanceAdvisoryRequest(
        long worstTickMillis,
        int playerCount,
        int dimensionCount,
        List<SubsystemLoad> subsystemLoads
) {

    public PerformanceAdvisoryRequest {
        subsystemLoads = List.copyOf(subsystemLoads);
    }

    /**
     * Description of a single subsystem that appears to be under load.
     */
    public record SubsystemLoad(String id, String summary, String mitigationGoal, long observedValue, String evidence) {
    }
}
