package com.thunder.wildernessodysseyapi.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Tracks corner structure block positions per structure name for faster lookup.
 */
public final class StructureBlockCornerCache {

    private static final java.util.Map<ServerLevel, StructureBlockCornerCache> CACHES = new java.util.WeakHashMap<>();

    private final java.util.Map<String, java.util.Set<BlockPos>> cornersByName = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, String> nameByCorner = new java.util.HashMap<>();

    private StructureBlockCornerCache() {
    }

    /**
     * Returns the cache associated with the given level, creating it when necessary.
     */
    public static StructureBlockCornerCache get(ServerLevel level) {
        synchronized (CACHES) {
            return CACHES.computeIfAbsent(level, key -> new StructureBlockCornerCache());
        }
    }

    /**
     * Returns the cache associated with the given level without creating one.
     */
    public static StructureBlockCornerCache getIfPresent(ServerLevel level) {
        synchronized (CACHES) {
            return CACHES.get(level);
        }
    }

    /**
     * Records a corner structure block for the supplied structure name.
     */
    public synchronized void addCorner(BlockPos position, String structureName) {
        String normalized = normalize(structureName);
        if (normalized == null) {
            return;
        }
        BlockPos immutable = position.immutable();
        String previousName = this.nameByCorner.put(immutable, normalized);
        if (previousName != null && !previousName.equals(normalized)) {
            java.util.Set<BlockPos> previousCorners = this.cornersByName.get(previousName);
            if (previousCorners != null) {
                previousCorners.remove(immutable);
                if (previousCorners.isEmpty()) {
                    this.cornersByName.remove(previousName);
                }
            }
        }
        java.util.Set<BlockPos> corners = this.cornersByName.computeIfAbsent(normalized,
                key -> new java.util.HashSet<>());
        corners.add(immutable);
    }

    /**
     * Removes a previously registered corner block from the cache.
     */
    public synchronized void removeCorner(BlockPos position) {
        BlockPos immutable = position.immutable();
        String name = this.nameByCorner.remove(immutable);
        if (name == null) {
            return;
        }
        java.util.Set<BlockPos> corners = this.cornersByName.get(name);
        if (corners == null) {
            return;
        }
        corners.remove(immutable);
        if (corners.isEmpty()) {
            this.cornersByName.remove(name);
        }
    }

    /**
     * Streams known corner positions for the provided structure name, sorted by manhattan distance.
     */
    public synchronized java.util.List<BlockPos> findCorners(String structureName, BlockPos origin, int maxDistance) {
        String normalized = normalize(structureName);
        if (normalized == null) {
            return java.util.Collections.emptyList();
        }
        java.util.Set<BlockPos> corners = this.cornersByName.get(normalized);
        if (corners == null || corners.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        int range = maxDistance < 0 ? Integer.MAX_VALUE : maxDistance;
        java.util.ArrayList<BlockPos> result = new java.util.ArrayList<>(corners.size());
        for (BlockPos corner : corners) {
            if (!isWithinRange(origin, corner, range)) {
                continue;
            }
            result.add(corner);
        }
        result.sort(java.util.Comparator.comparingInt(pos -> pos.distManhattan(origin)));
        return result;
    }

    private static boolean isWithinRange(BlockPos origin, BlockPos candidate, int range) {
        int dx = Math.abs(candidate.getX() - origin.getX());
        int dy = Math.abs(candidate.getY() - origin.getY());
        int dz = Math.abs(candidate.getZ() - origin.getZ());
        return dx <= range && dy <= range && dz <= range;
    }

    private static String normalize(String name) {
        if (name == null) {
            return null;
        }
        if (name.isEmpty()) {
            return null;
        }
        return name;
    }
}
