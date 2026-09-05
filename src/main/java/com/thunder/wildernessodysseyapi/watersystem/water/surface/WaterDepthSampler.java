package com.thunder.wildernessodysseyapi.watersystem.water.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded server bathymetry for runtime-edited generated columns. Unmodified
 * columns keep their existing generated floor. A sparse revision invalidates
 * only the affected cached chunk's results; depth work is never done per vertex.
 */
public final class WaterDepthSampler {
    private static final int MAX_COLUMNS = 4_096;
    private static final int MAX_SCANS_PER_TICK = 32;
    private static final int MAX_DEPTH = 64;
    private static final Map<ServerLevel, Cache> LEVELS = new IdentityHashMap<>();
    private WaterDepthSampler() { }

    /** Resolves the first solid floor of an already-loaded edited column. */
    public static int floor(ServerLevel level, int x, int z, int surfaceY, int fallbackFloor, long revision) {
        Cache cache = LEVELS.computeIfAbsent(level, ignored -> new Cache());
        long now = level.getGameTime();
        if (cache.tick != now) { cache.tick = now; cache.scans = 0; }
        long key = BlockPos.asLong(x, surfaceY, z);
        Depth cached = cache.columns.get(key);
        if (cached != null && cached.revision == revision && now >= cached.tick && now - cached.tick < 40) return cached.floor;
        if (cache.scans >= MAX_SCANS_PER_TICK) return cached == null ? fallbackFloor : cached.floor;
        BlockPos.MutableBlockPos cursor = cache.cursor;
        cursor.set(x, surfaceY, z);
        if (!level.hasChunkAt(cursor)) { cache.columns.remove(key); return fallbackFloor; }
        cache.scans++;
        int floor = Math.max(level.getMinBuildHeight(), surfaceY - MAX_DEPTH);
        for (int y = surfaceY - 1; y >= floor; y--) {
            cursor.set(x, y, z);
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) { floor = y; break; }
        }
        if (cache.columns.size() >= MAX_COLUMNS && !cache.columns.containsKey(key)) {
            cache.columns.remove(cache.columns.keySet().iterator().next());
        }
        cache.columns.put(key, new Depth(floor, revision, now));
        return floor;
    }

    /** Invalidates the edited column without discarding distant bathymetry. */
    public static void invalidate(ServerLevel level, BlockPos position) {
        Cache cache = LEVELS.get(level);
        if (cache == null) return;
        cache.columns.keySet().removeIf(key -> BlockPos.getX(key) == position.getX() && BlockPos.getZ(key) == position.getZ());
    }

    /** Drops loaded-only sampling state on dimension teardown. */
    public static void clear(ServerLevel level) { LEVELS.remove(level); }

    private static final class Cache {
        private final Map<Long, Depth> columns = new LinkedHashMap<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private long tick = Long.MIN_VALUE;
        private int scans;
    }
    private record Depth(int floor, long revision, long tick) { }
}
