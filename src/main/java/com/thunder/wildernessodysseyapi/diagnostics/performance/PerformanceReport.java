package com.thunder.wildernessodysseyapi.diagnostics.performance;

import java.util.List;
import java.util.Map;

/** Detached report input: only bounded snapshots, primitive values and strings may reach the IO pool. */
public record PerformanceReport(int schemaVersion, String sessionId, String startedAt, String outcome,
                                boolean safeMode, String observation, Map<String, String> environment,
                                Map<PerformanceArea, PerformanceOwnershipRegistry.Ownership> ownership,
                                PerformanceObserver.Snapshot performance, PerformanceTimings.Snapshot timings,
                                Map<String, Long> workQueues, List<Sample> history, String saveBacklog, List<String> limits) {
    public PerformanceReport {
        environment = Map.copyOf(environment);
        ownership = Map.copyOf(ownership);
        workQueues = Map.copyOf(workQueues);
        history = List.copyOf(history);
        limits = List.copyOf(limits);
    }

    public record Sample(long elapsedMillis, PerformanceObserver.Snapshot performance, Map<String, Long> workQueues) {
        public Sample { workQueues = Map.copyOf(workQueues); }
    }
}
