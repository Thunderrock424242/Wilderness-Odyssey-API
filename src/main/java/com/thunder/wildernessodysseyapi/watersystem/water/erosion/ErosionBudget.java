package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Rolling dimension mutation limit and per-chunk cooldown, bounded by recent changes. */
public final class ErosionBudget {
    private final ArrayDeque<Change> recent = new ArrayDeque<>();
    private final Map<Long, Long> chunks = new HashMap<>();

    /** Checks capacity without spending it; failed world edits consume no material or budget. */
    public boolean allows(long tick, long chunk, int limit) {
        prune(tick);
        return limit > 0 && recent.size() < Math.min(16, limit) && !chunks.containsKey(chunk);
    }

    /** Records a successfully applied terrain change. */
    public void record(long tick, long chunk) {
        prune(tick);
        if (recent.size() >= 16) throw new IllegalStateException("Erosion budget was not checked");
        recent.addLast(new Change(tick, chunk));
        chunks.put(chunk, tick);
    }

    private void prune(long tick) {
        while (!recent.isEmpty() && (tick < recent.getFirst().tick || tick - recent.getFirst().tick >= 1_200L)) {
            Change expired = recent.removeFirst();
            chunks.remove(expired.chunk, expired.tick);
        }
    }

    private record Change(long tick, long chunk) { }
}
