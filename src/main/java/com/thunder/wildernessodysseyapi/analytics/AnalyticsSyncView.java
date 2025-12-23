package com.thunder.wildernessodysseyapi.analytics;

import com.thunder.wildernessodysseyapi.AI.AI_perf.PerformanceAdvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compact analytics view intended for client/relay sync. Keeps the full snapshot server-side
 * while sending only lightweight diffs and short enums over the wire.
 */
public class AnalyticsSyncView {

    public long timestampMillis;
    public int playerCount;
    public int maxPlayers;
    public long usedMemoryMb;
    public long totalMemoryMb;
    public long peakMemoryMb;
    public int recommendedMemoryMb;
    public long worstTickMillis;
    public double cpuLoad;
    public HealthStatus status = HealthStatus.OK;
    public List<String> joinedPlayerIds = Collections.emptyList();
    public List<String> leftPlayerIds = Collections.emptyList();

    public enum HealthStatus {
        OK,
        WARM,
        HOT
    }

    public static AnalyticsSyncView fromSnapshots(AnalyticsSnapshot current, AnalyticsSnapshot previous) {
        AnalyticsSyncView view = new AnalyticsSyncView();
        view.timestampMillis = current.timestampMillis;
        view.playerCount = current.playerCount;
        view.maxPlayers = current.maxPlayers;
        view.usedMemoryMb = current.usedMemoryMb;
        view.totalMemoryMb = current.totalMemoryMb;
        view.peakMemoryMb = current.peakMemoryMb;
        view.recommendedMemoryMb = current.recommendedMemoryMb;
        view.worstTickMillis = current.worstTickMillis;
        view.cpuLoad = current.cpuLoad;
        view.status = resolveStatus(current);
        view.joinedPlayerIds = diffPlayers(current, previous, true);
        view.leftPlayerIds = diffPlayers(current, previous, false);
        return view;
    }

    private static HealthStatus resolveStatus(AnalyticsSnapshot snapshot) {
        if (snapshot.overloaded) {
            return HealthStatus.HOT;
        }
        if (snapshot.worstTickMillis >= PerformanceAdvisor.DEFAULT_TICK_BUDGET_MS) {
            return HealthStatus.WARM;
        }
        return HealthStatus.OK;
    }

    private static List<String> diffPlayers(AnalyticsSnapshot current, AnalyticsSnapshot previous, boolean joined) {
        Set<String> currentIds = collectIds(current);
        Set<String> previousIds = collectIds(previous);

        Set<String> delta = new HashSet<>();
        if (joined) {
            for (String id : currentIds) {
                if (!previousIds.contains(id)) {
                    delta.add(id);
                }
            }
        } else {
            for (String id : previousIds) {
                if (!currentIds.contains(id)) {
                    delta.add(id);
                }
            }
        }

        if (delta.isEmpty()) {
            return Collections.emptyList();
        }
        return delta.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
    }

    private static Set<String> collectIds(AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.players == null) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>();
        snapshot.players.forEach(player -> ids.add(player.uuid));
        return ids;
    }
}
