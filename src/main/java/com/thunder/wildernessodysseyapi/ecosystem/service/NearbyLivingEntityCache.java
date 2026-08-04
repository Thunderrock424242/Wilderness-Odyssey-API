package com.thunder.wildernessodysseyapi.ecosystem.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Short-lived, section-keyed entity query cache shared by ecosystem services.
 *
 * <p>Entries live for at most one second and are distance-filtered again for
 * each caller. The cache avoids repeating the same broad level query for a herd
 * clustered in one area while never becoming authoritative entity storage.</p>
 */
final class NearbyLivingEntityCache {

    private static final int MAXIMUM_ENTRIES_PER_LEVEL = 512;
    private static final long CACHE_TICKS = 20L;
    private final Map<ServerLevel, Map<Long, Entry>> levels = new WeakHashMap<>();

    List<LivingEntity> query(ServerLevel level, BlockPos center, int radius, long gameTime) {
        int radiusBucket = Math.max(1, (radius + 7) / 8);
        long section = SectionPos.asLong(
                SectionPos.blockToSectionCoord(center.getX()),
                SectionPos.blockToSectionCoord(center.getY()),
                SectionPos.blockToSectionCoord(center.getZ())
        );
        long key = section ^ ((long) radiusBucket * 0x9E37_79B9_7F4A_7C15L);
        Map<Long, Entry> cache = levels.computeIfAbsent(level, ignored -> new HashMap<>());
        Entry entry = cache.get(key);
        if (entry == null || entry.expiresAt() <= gameTime) {
            int queryRadius = radiusBucket * 8;
            List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(center).inflate(queryRadius),
                    LivingEntity::isAlive
            );
            entry = new Entry(gameTime + CACHE_TICKS, List.copyOf(entities));
            cache.put(key, entry);
            trim(cache);
        }

        double maximumDistanceSquared = (double) radius * radius;
        List<LivingEntity> filtered = new ArrayList<>();
        for (LivingEntity entity : entry.entities()) {
            if (entity.isAlive()
                    && entity.level() == level
                    && entity.blockPosition().distSqr(center) <= maximumDistanceSquared) {
                filtered.add(entity);
            }
        }
        return filtered;
    }

    void clear(ServerLevel level) {
        levels.remove(level);
    }

    private static void trim(Map<Long, Entry> cache) {
        while (cache.size() > MAXIMUM_ENTRIES_PER_LEVEL) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private record Entry(long expiresAt, List<LivingEntity> entities) {
    }
}
