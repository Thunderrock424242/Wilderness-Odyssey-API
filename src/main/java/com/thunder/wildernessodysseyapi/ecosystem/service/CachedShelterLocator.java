package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterLocator;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Finds standable canopy, cave, or solid-roof shelter through bounded cached scans. */
public final class CachedShelterLocator implements ShelterLocator {

    private static final long CACHE_TICKS = 300L;
    private static final int MAXIMUM_CACHE_ENTRIES = 384;
    private static final int MAXIMUM_SAMPLES = 640;
    private final Map<ServerLevel, Map<Long, Entry>> levels = new WeakHashMap<>();

    @Override
    public Optional<EnvironmentalContext.ShelterTarget> find(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            int radius
    ) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        BlockPos origin = animal.blockPosition();
        long gameTime = level.getGameTime();
        long key = cacheKey(origin, radius);
        Map<Long, Entry> cache = levels.computeIfAbsent(level, ignored -> new HashMap<>());
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > gameTime && isShelter(level, cached.target().position())) {
            return Optional.of(cached.target());
        }

        EnvironmentalContext.ShelterTarget target = search(level, origin, radius);
        if (target != null) {
            cache.put(key, new Entry(gameTime + CACHE_TICKS, target));
            trim(cache);
        }
        return Optional.ofNullable(target);
    }

    /** Releases cached positions for an unloading level. */
    public void clear(ServerLevel level) {
        levels.remove(level);
    }

    private static EnvironmentalContext.ShelterTarget search(ServerLevel level, BlockPos origin, int radius) {
        int samples = 0;
        for (int ring = 0; ring <= radius && samples < MAXIMUM_SAMPLES; ring++) {
            for (int dx = -ring; dx <= ring && samples < MAXIMUM_SAMPLES; dx++) {
                for (int dzSign = -1; dzSign <= 1; dzSign += 2) {
                    int dz = ring * dzSign;
                    if (ring == 0 && dzSign > -1) {
                        continue;
                    }
                    for (int dy = 2; dy >= -4; dy--) {
                        samples++;
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (isShelter(level, candidate)) {
                            return new EnvironmentalContext.ShelterTarget(candidate, coverBlocks(level, candidate));
                        }
                    }
                }
            }
            for (int dz = -ring + 1; dz < ring && samples < MAXIMUM_SAMPLES; dz++) {
                for (int dxSign = -1; dxSign <= 1; dxSign += 2) {
                    int dx = ring * dxSign;
                    for (int dy = 2; dy >= -4; dy--) {
                        samples++;
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (isShelter(level, candidate)) {
                            return new EnvironmentalContext.ShelterTarget(candidate, coverBlocks(level, candidate));
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isShelter(ServerLevel level, BlockPos position) {
        return level.isInWorldBounds(position)
                && level.hasChunkAt(position)
                && CachedWaterSourceLocator.isStandable(level, position)
                && !level.getFluidState(position).is(FluidTags.WATER)
                && !level.canSeeSky(position.above());
    }

    private static int coverBlocks(ServerLevel level, BlockPos position) {
        BlockPos.MutableBlockPos cursor = position.above().mutable();
        for (int distance = 1; distance <= 12; distance++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return distance;
            }
            cursor.move(Direction.UP);
        }
        return 12;
    }

    private static long cacheKey(BlockPos position, int radius) {
        long section = SectionPos.asLong(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getY()),
                SectionPos.blockToSectionCoord(position.getZ())
        );
        return section ^ ((long) Math.max(1, radius / 8) << 52);
    }

    private static void trim(Map<Long, Entry> cache) {
        while (cache.size() > MAXIMUM_CACHE_ENTRIES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private record Entry(long expiresAt, EnvironmentalContext.ShelterTarget target) {
    }
}
