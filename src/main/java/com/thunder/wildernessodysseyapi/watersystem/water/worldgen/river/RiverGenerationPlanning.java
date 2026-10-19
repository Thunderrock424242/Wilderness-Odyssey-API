package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.dataengine.cache.DataCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import java.util.Map;
import java.util.EnumMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded world-owned immutable prediction cache; no executor, live chunk references or future waits. */
public final class RiverGenerationPlanning {
    private static final int VERSION = 4;
    private static final Map<ServerLevel, WorldCache> WORLDS = new WeakHashMap<>();
    private RiverGenerationPlanning() { }

    public static Result plan(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState random,
                              int minimumX, int minimumZ, RiverGenerationSettings generation,
                              RiverConnectedPlanner.Settings planning, int maximumDrop) {
        WorldCache cache = cache(level);
        var key = new PlanKey(level.getSeed(), level.dimension().location().toString(), generator, random,
                generation.fingerprint(), planning, maximumDrop, minimumX, minimumZ, VERSION);
        var cached = cache.plans.get(key, 0).orElse(null);
        if (cached != null) return cached;
        long start = System.nanoTime();
        RiverConnectedPlanner.TerrainSampler sampler = (x, z) -> {
            var sampleKey = new SampleKey(generator, random, x, z, level.getMinBuildHeight(), level.getHeight(), VERSION);
            var sample = cache.terrain.get(sampleKey, 0).orElse(null);
            if (sample == null) {
                sample = RiverNoiseTerrain.predict(generator, level, random, x, z);
                cache.terrain.put(sampleKey, sample);
            }
            return sample;
        };
        var planned = RiverSourcePlanning.plan(level.getSeed(), minimumX, minimumZ, generation, planning, maximumDrop, sampler);
        Result result = new Result(planned.topology(), planned.shape());
        cache.plans.put(key, result);
        cache.planningNanos.add(System.nanoTime() - start);
        cache.calculations.increment();
        if (planned.topology().budgetExceeded()) cache.rejected.increment();
        cache.rejectedRoutes.add(planned.rejectedRoutes());
        cache.rasterVisits.add(planned.rasterVisits());
        return result;
    }

    /** Drops only the unloading world's predictions and counters. */
    public static synchronized void clear(ServerLevel level) { WORLDS.remove(level); }

    public static Statistics statistics(ServerLevel level) {
        WorldCache cache;
        synchronized (RiverGenerationPlanning.class) { cache = WORLDS.get(level); }
        return cache == null ? new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0)
                : new Statistics(cache.terrainHits.sum(), cache.terrainMisses.sum(), cache.planHits.sum(),
                cache.calculations.sum(), cache.rejected.sum(), cache.terrain.size(), cache.planningNanos.sum(),
                cache.rejectedRoutes.sum(), cache.rasterVisits.sum());
    }

    /** Fixed enum counters and one last position per reason, bounded independently of generated area. */
    public static void recordRejection(ServerLevel level, RiverConnectedGeneration.Rejection rejection) {
        WorldCache cache = cache(level);
        synchronized (cache.rejections) { cache.rejections.put(rejection.reason(),
                new RejectionCount(cache.rejections.getOrDefault(rejection.reason(), new RejectionCount(0, rejection)).count() + 1, rejection)); }
    }

    public static Map<RiverConnectedGeneration.Reason, RejectionCount> rejections(ServerLevel level) {
        WorldCache cache;
        synchronized (RiverGenerationPlanning.class) { cache = WORLDS.get(level); }
        if (cache == null) return Map.of();
        synchronized (cache.rejections) { return Map.copyOf(cache.rejections); }
    }

    public record RejectionCount(long count, RiverConnectedGeneration.Rejection latest) { }

    private static synchronized WorldCache cache(ServerLevel level) {
        WorldCache found = WORLDS.get(level);
        if (found != null) return found;
        // Only a handful of dimensions normally generate rivers. Weak keys and
        // explicit unload cleanup are backed by a hard installation-wide bound.
        if (WORLDS.size() >= 8) WORLDS.clear();
        var created = new WorldCache();
        WORLDS.put(level, created);
        return created;
    }

    public record Result(RiverConnectedPlanner.Plan topology, RiverChannelShape.Shape shape) { }
    public record Statistics(long terrainHits, long terrainMisses, long planHits, long calculations,
                             long rejectedPlans, int retainedTerrainSamples, long planningNanos,
                             long rejectedRoutes, long rasterVisits) { }
    private record SampleKey(NoiseBasedChunkGenerator generator, RandomState random, int x, int z,
                             int minimumY, int height, int version) { }
    private record PlanKey(long seed, String dimension, NoiseBasedChunkGenerator generator, RandomState random,
                           long generation, RiverConnectedPlanner.Settings planning, int drop,
                           int x, int z, int version) { }
    private static final class WorldCache {
        final Map<RiverConnectedGeneration.Reason, RejectionCount> rejections = new EnumMap<>(RiverConnectedGeneration.Reason.class);
        final LongAdder terrainHits = new LongAdder();
        final LongAdder terrainMisses = new LongAdder();
        final LongAdder planHits = new LongAdder();
        final LongAdder calculations = new LongAdder();
        final LongAdder planningNanos = new LongAdder();
        final LongAdder rejected = new LongAdder();
        final LongAdder rejectedRoutes = new LongAdder();
        final LongAdder rasterVisits = new LongAdder();
        final DataCache<SampleKey, RiverConnectedPlanner.Terrain> terrain =
                new DataCache<>(8192, terrainHits::increment, terrainMisses::increment, ignored -> { });
        final DataCache<PlanKey, Result> plans = new DataCache<>(128, planHits::increment, () -> { }, ignored -> { });
    }
}
