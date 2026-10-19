package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Complete predicted source admission; no world, chunks, cache owner or executor. */
public final class RiverSourcePlanning {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private RiverSourcePlanning() { }

    public static Plan plan(long seed, int minimumX, int minimumZ, RiverGenerationSettings generation,
                             RiverConnectedPlanner.Settings planning, int maximumDrop,
                             RiverConnectedPlanner.TerrainSampler provider) {
        var empty = new RiverChannelShape.Shape(List.of(), maximumDrop, generation.waterfalls());
        if (!generation.enabled()) return new Plan(new RiverConnectedPlanner.Plan(List.of(), 0, 0, false), empty, 0, 0, 0);
        Map<Long, RiverConnectedPlanner.Terrain> samples = new HashMap<>();
        RiverConnectedPlanner.TerrainSampler terrain = (x, z) -> {
            long key = key(x, z);
            if (samples.containsKey(key)) return samples.get(key);
            if (samples.size() >= planning.maximumSamples()) throw new WorkLimit();
            var sample = provider.sample(x, z);
            samples.put(key, sample);
            return sample;
        };
        var raster = new RiverChannelShape.RasterBudget(planning.maximumSamples() * 128);
        var geometry = new RiverChannelShape.Accumulator();
        int[] counts = new int[2];
        try {
            var topology = RiverConnectedPlanner.plan(seed, minimumX, minimumZ, 32, planning, terrain, route -> {
                var shape = RiverChannelShape.rasterComplete(route, generation.streamDepth(), maximumDrop,
                        generation.waterfalls(), terrain, raster);
                if (raster.exhausted()) throw new WorkLimit();
                if (shape.columns().isEmpty() || !fits(shape, terrain, generation.maximumCut())) {
                    counts[0]++;
                    return false;
                }
                if (!geometry.add(shape)) throw new WorkLimit();
                counts[1]++;
                return true;
            });
            if (topology.budgetExceeded()) return exhausted(samples.size(), raster.visits(), counts);
            topology = new RiverConnectedPlanner.Plan(topology.edges(), topology.certifiedRoutes(), samples.size(), false);
            return new Plan(topology, geometry.finish(minimumX, minimumZ, 32, maximumDrop, generation.waterfalls()),
                    counts[0], raster.visits(), counts[1]);
        } catch (WorkLimit ignored) {
            // Neither coarse nor fine exhaustion may retain callback output.
            return exhausted(samples.size(), raster.visits(), counts);
        }
    }

    private static Plan exhausted(int samples, int visits, int[] counts) {
        return new Plan(new RiverConnectedPlanner.Plan(List.of(), 0, samples, true),
                new RiverChannelShape.Shape(List.of()), counts[0], visits, counts[1]);
    }

    private static boolean fits(RiverChannelShape.Shape shape, RiverConnectedPlanner.TerrainSampler terrain, int maximumCut) {
        Map<Long, RiverChannelShape.Column> columns = new HashMap<>();
        for (var column : shape.columns()) columns.put(key(column.x(), column.z()), column);
        for (var column : shape.columns()) {
            var home = terrain.sample(column.x(), column.z());
            if (home == null) return false;
            if (home.water()) {
                if (home.height() != column.waterY() || column.topY() != column.waterY()) return false;
                continue;
            }
            if (column.waterY() > home.height() || home.height() - column.floorY() > maximumCut) return false;
            for (int[] direction : DIRECTIONS) {
                int x = column.x() + direction[0];
                int z = column.z() + direction[1];
                var next = columns.get(key(x, z));
                if (next != null && column.waterY() > next.floorY() && column.waterY() <= next.topY()) continue;
                var bank = terrain.sample(x, z);
                if (bank == null || bank.water() && bank.height() != column.waterY()
                        || !bank.water() && bank.height() < column.waterY()) return false;
            }
        }
        return true;
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    private static final class WorkLimit extends RuntimeException {
        WorkLimit() { super(null, null, false, false); }
    }
    public record Plan(RiverConnectedPlanner.Plan topology, RiverChannelShape.Shape shape,
                       int rejectedRoutes, int rasterVisits, int certifiedBeforeExhaustion) { }
}
