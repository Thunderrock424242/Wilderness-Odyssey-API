package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.EcosystemTags;
import com.thunder.wildernessodysseyapi.ecosystem.api.FoodAvailabilityService;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Cached forage scoring and prey population selection for the initial profiles. */
public final class CachedFoodAvailabilityService implements FoodAvailabilityService {

    private static final long FORAGE_CACHE_TICKS = 200L;
    private static final int MAXIMUM_FORAGE_SAMPLES = 96;
    private static final int MAXIMUM_CACHE_ENTRIES = 384;
    private final NearbyLivingEntityCache nearbyEntities;
    private final Map<ServerLevel, Map<Long, ForageEntry>> forageLevels = new WeakHashMap<>();

    CachedFoodAvailabilityService(NearbyLivingEntityCache nearbyEntities) {
        this.nearbyEntities = nearbyEntities;
    }

    @Override
    public double availability(PathfinderMob animal, int radius) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return 0.0;
        }
        BlockPos origin = animal.blockPosition();
        long key = cacheKey(origin, radius);
        long gameTime = level.getGameTime();
        Map<Long, ForageEntry> cache = forageLevels.computeIfAbsent(level, ignored -> new HashMap<>());
        ForageEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > gameTime) {
            return cached.availability();
        }

        int samples = 0;
        int forage = 0;
        int stride = Math.max(1, radius / 4);
        for (int dx = -radius; dx <= radius && samples < MAXIMUM_FORAGE_SAMPLES; dx += stride) {
            for (int dz = -radius; dz <= radius && samples < MAXIMUM_FORAGE_SAMPLES; dz += stride) {
                BlockPos horizontal = origin.offset(dx, 0, dz);
                if (!level.hasChunkAt(horizontal)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, horizontal.getX(), horizontal.getZ()) - 1;
                BlockPos surface = new BlockPos(horizontal.getX(), y, horizontal.getZ());
                samples++;
                if (level.getBlockState(surface).is(EcosystemTags.FORAGE_BLOCKS)
                        || level.getBlockState(surface.below()).is(EcosystemTags.FORAGE_BLOCKS)) {
                    forage++;
                }
            }
        }
        double availability = samples == 0 ? 0.0 : forage / (double) samples;
        cache.put(key, new ForageEntry(gameTime + FORAGE_CACHE_TICKS, availability));
        trim(cache);
        return availability;
    }

    @Override
    public PredatorFoodSample prey(PathfinderMob predator, SpeciesBehaviorProfile profile, int radius) {
        if (!(predator.level() instanceof ServerLevel level) || profile.predator().preyTags().isEmpty()) {
            return new PredatorFoodSample(List.of(), Optional.empty());
        }
        List<TagKey<EntityType<?>>> preyTags = profile.predator().preyTags().stream()
                .map(id -> TagKey.create(Registries.ENTITY_TYPE, id))
                .toList();
        List<LivingEntity> population = new ArrayList<>();
        for (LivingEntity candidate : nearbyEntities.query(
                level, predator.blockPosition(), radius, level.getGameTime())) {
            if (candidate == predator || !candidate.isAlive()) {
                continue;
            }
            if (candidate instanceof AgeableMob ageable && ageable.isBaby()) {
                continue;
            }
            if (preyTags.stream().anyMatch(candidate.getType().builtInRegistryHolder()::is)) {
                population.add(candidate);
            }
        }
        population.sort(Comparator.comparingDouble(predator::distanceToSqr));
        return new PredatorFoodSample(
                List.copyOf(population),
                population.isEmpty() ? Optional.empty() : Optional.of(population.getFirst())
        );
    }

    /** Releases forage scores for an unloading level. */
    public void clear(ServerLevel level) {
        forageLevels.remove(level);
    }

    private static long cacheKey(BlockPos position, int radius) {
        long section = SectionPos.asLong(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getY()),
                SectionPos.blockToSectionCoord(position.getZ())
        );
        return section ^ ((long) Math.max(1, radius / 8) << 48);
    }

    private static void trim(Map<Long, ForageEntry> cache) {
        while (cache.size() > MAXIMUM_CACHE_ENTRIES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private record ForageEntry(long expiresAt, double availability) {
    }
}
