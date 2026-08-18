package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterLocator;
import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterPreference;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
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
        return find(animal, profile, radius, ShelterPreference.ANY_COVER);
    }

    /** Finds the nearest preferred cover with a bounded nearest-cover fallback. */
    @Override
    public Optional<EnvironmentalContext.ShelterTarget> find(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            int radius,
            ShelterPreference preference
    ) {
        ShelterPreference requested = preference == null ? ShelterPreference.ANY_COVER : preference;
        if (requested == ShelterPreference.NONE) {
            return Optional.empty();
        }
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        BlockPos origin = animal.blockPosition();
        long gameTime = level.getGameTime();
        long key = cacheKey(origin, radius, requested);
        Map<Long, Entry> cache = levels.computeIfAbsent(level, ignored -> new HashMap<>());
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > gameTime && isShelter(level, cached.target().position())) {
            return Optional.of(cached.target());
        }

        EnvironmentalContext.ShelterTarget target = search(level, origin, radius, requested);
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

    private static EnvironmentalContext.ShelterTarget search(
            ServerLevel level,
            BlockPos origin,
            int radius,
            ShelterPreference preference
    ) {
        int samples = 0;
        EnvironmentalContext.ShelterTarget fallback = null;
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
                            EnvironmentalContext.ShelterTarget target = new EnvironmentalContext.ShelterTarget(
                                    candidate,
                                    coverBlocks(level, candidate)
                            );
                            if (fallback == null) {
                                fallback = target;
                            }
                            if (matchesPreference(level, candidate, preference)) {
                                return target;
                            }
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
                            EnvironmentalContext.ShelterTarget target = new EnvironmentalContext.ShelterTarget(
                                    candidate,
                                    coverBlocks(level, candidate)
                            );
                            if (fallback == null) {
                                fallback = target;
                            }
                            if (matchesPreference(level, candidate, preference)) {
                                return target;
                            }
                        }
                    }
                }
            }
        }
        return fallback;
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

    private static boolean matchesPreference(
            ServerLevel level,
            BlockPos position,
            ShelterPreference preference
    ) {
        if (preference == ShelterPreference.ANY_COVER) {
            return true;
        }
        BlockPos roof = firstRoof(level, position);
        if (roof == null) {
            return false;
        }
        boolean leaves = level.getBlockState(roof).is(BlockTags.LEAVES);
        return switch (preference) {
            case DENSE_CANOPY -> leaves;
            case SOLID_OVERHEAD -> !leaves;
            case ANY_COVER -> true;
            case NONE -> false;
        };
    }

    private static BlockPos firstRoof(ServerLevel level, BlockPos position) {
        BlockPos.MutableBlockPos cursor = position.above().mutable();
        for (int distance = 1; distance <= 12; distance++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return cursor.immutable();
            }
            cursor.move(Direction.UP);
        }
        return null;
    }

    private static long cacheKey(BlockPos position, int radius, ShelterPreference preference) {
        long section = SectionPos.asLong(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getY()),
                SectionPos.blockToSectionCoord(position.getZ())
        );
        return section
                ^ ((long) Math.max(1, radius / 8) << 52)
                ^ ((long) preference.ordinal() * 0xC2B2_AE3D_27D4_EB4FL);
    }

    private static void trim(Map<Long, Entry> cache) {
        while (cache.size() > MAXIMUM_CACHE_ENTRIES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private record Entry(long expiresAt, EnvironmentalContext.ShelterTarget target) {
    }
}
