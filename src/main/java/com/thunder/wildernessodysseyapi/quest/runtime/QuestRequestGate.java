package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Server-tick token bucket and bounded request replay window, released on logout. */
public final class QuestRequestGate {
    private static final class Window {
        final LinkedHashSet<UUID> requests = new LinkedHashSet<>();
        double tokens;
        long tick;
        long viewTick = -40;
        Window(long tick, int burst) { this.tick = tick; tokens = burst; }
    }
    private final Map<UUID, Window> players = new HashMap<>();
    public boolean admit(UUID player, UUID request, long tick, boolean view, QuestConfig.Settings settings) {
        var window = players.computeIfAbsent(player, ignored -> new Window(tick, settings.burst()));
        if (window.requests.contains(request)) return false;
        window.tokens = Math.min(settings.burst(), window.tokens + Math.max(0, tick - window.tick) * settings.requestsPerSecond() / 20.0);
        window.tick = tick;
        if (window.tokens < 1 || view && tick - window.viewTick < 40) return false;
        window.tokens--; if (view) window.viewTick = tick;
        window.requests.add(request); if (window.requests.size() > 128) window.requests.remove(window.requests.iterator().next());
        return true;
    }
    public void clear(UUID player) { players.remove(player); }
    public void clear() { players.clear(); }
}
