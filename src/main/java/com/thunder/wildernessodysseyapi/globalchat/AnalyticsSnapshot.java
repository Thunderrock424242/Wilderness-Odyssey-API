package com.thunder.wildernessodysseyapi.globalchat;

import java.util.List;

/**
 * Lightweight analytics payload describing current server health and players.
 * Uses public fields for simple JSON serialization.
 */
public class AnalyticsSnapshot {
    public long timestampMillis;
    public List<PlayerStats> players;
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

    public static class PlayerStats {
        public String uuid;
        public String name;
    }
}
