package com.thunder.wildernessodysseyapi.globalchat;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot of recent analytics events to keep the relay in sync.
 */
public class AnalyticsSyncView {
    public HealthStatus status = HealthStatus.HEALTHY;
    public List<String> joinedPlayerIds = Collections.emptyList();
    public List<String> leftPlayerIds = Collections.emptyList();

    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
}
