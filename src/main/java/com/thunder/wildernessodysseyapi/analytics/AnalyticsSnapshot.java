package com.thunder.wildernessodysseyapi.analytics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of server performance and usage metrics that can be
 * shared with external tools or privileged users.
 */
public class AnalyticsSnapshot {

    public long timestampMillis = Instant.now().toEpochMilli();
    public int playerCount;
    public int maxPlayers;
    public long usedMemoryMb;
    public long totalMemoryMb;
    public long peakMemoryMb;
    public int recommendedMemoryMb;
    public long worstTickMillis;
    public double cpuLoad;
    public boolean overloaded;

    public String overloadedReason;

    public List<PlayerStats> players = new ArrayList<>();

    public static class PlayerStats {
        public String uuid;
        public String name;
        public int pingMillis;
        public String dimension;
    }
}
