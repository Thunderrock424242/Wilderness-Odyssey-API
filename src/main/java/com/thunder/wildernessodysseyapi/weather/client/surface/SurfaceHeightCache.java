package com.thunder.wildernessodysseyapi.weather.client.surface;

import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/** Lazy camera-local terrain cache with a hard budget on actual height probes. */
final class SurfaceHeightCache {
    private static final long REFRESH_TICKS = 20;
    private final Map<Long, Height> heights = new HashMap<>();
    private long tick;
    private int remaining;
    private int probes;

    void beginRefresh(int centerX, int centerZ, int radius, long tick, int budget) {
        this.tick = tick;
        remaining = Math.max(0, budget);
        probes = 0;
        heights.keySet().removeIf(key -> Math.abs((long) ChunkPos.getX(key) - centerX) > radius + 1L
                || Math.abs((long) ChunkPos.getZ(key) - centerZ) > radius + 1L);
    }

    int height(int x, int z, IntBinaryOperator sampler) {
        long key = ChunkPos.asLong(x, z);
        Height cached = heights.get(key);
        if (cached != null && tick >= cached.tick && tick - cached.tick < REFRESH_TICKS) return cached.value;
        if (remaining <= 0) return Integer.MIN_VALUE;
        remaining--;
        probes++;
        int value = sampler.applyAsInt(x, z);
        heights.put(key, new Height(value, tick));
        return value;
    }

    int probesThisRefresh() { return probes; }
    int size() { return heights.size(); }
    boolean exhausted() { return remaining == 0; }
    void clear() { heights.clear(); probes = 0; remaining = 0; }
    private record Height(int value, long tick) { }
}
