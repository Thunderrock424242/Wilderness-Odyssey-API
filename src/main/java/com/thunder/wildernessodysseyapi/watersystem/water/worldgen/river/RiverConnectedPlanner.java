package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chunk-independent, bounded source-to-receiver topology over supported terrain
 * predictions. Every admitted source has a complete strictly descending route
 * to predicted existing water. A path that reaches the work frontier is rejected
 * in its entirety. Prediction never grants permission to overwrite live terrain.
 */
public final class RiverConnectedPlanner {
    public static final int SPACING = 16;
    private RiverConnectedPlanner() { }

    public static Plan plan(long seed, int minimumX, int minimumZ, int size,
                            Settings settings, TerrainSampler sampler) {
        return plan(seed, minimumX, minimumZ, size, settings, sampler, route -> true);
    }

    /** Complete route admission precedes accumulation and tile clipping. */
    public static Plan plan(long seed, int minimumX, int minimumZ, int size,
                            Settings settings, TerrainSampler sampler, RouteValidator validator) {
        if (size < 16 || size > 32 || Math.floorMod(minimumX, 16) != 0
                || Math.floorMod(minimumZ, 16) != 0 || Math.abs((long) minimumX) > 29_999_968
                || Math.abs((long) minimumZ) > 29_999_968) throw new IllegalArgumentException("Expected aligned generation slice");
        if (settings.frequency() == 0) return new Plan(List.of(), 0, 0, false);
        Query query = new Query(settings, sampler);
        // A route is at most maximumSteps edges long. Include every possible
        // source of an edge whose width can intersect this slice, not merely
        // sources whose center happens to lie in the requesting chunk.
        int halo = settings.maximumSteps() + 2;
        int minCellX = Math.floorDiv(minimumX, SPACING) - halo;
        int minCellZ = Math.floorDiv(minimumZ, SPACING) - halo;
        int maxCellX = Math.floorDiv(minimumX + size - 1, SPACING) + halo;
        int maxCellZ = Math.floorDiv(minimumZ + size - 1, SPACING) + halo;
        Map<Long, MutableEdge> edges = new TreeMap<>();
        int routes = 0;
        try {
            for (int z = minCellZ; z <= maxCellZ; z++) for (int x = minCellX; x <= maxCellX; x++) {
                Point source = new Point(x, z);
                Terrain terrain = query.terrain(source);
                if (terrain == null || terrain.water()) continue;
                double wetProbability = settings.frequency() * (0.08 + 0.92 * terrain.rainfall());
                if (unit(mix(seed ^ source.key())) >= wetProbability
                        || query.area(source, Math.min(4, settings.minimumArea() - 1)) < settings.minimumArea()) continue;
                List<Point> path = query.route(source);
                if (path.isEmpty()) continue;
                long basin = mix(seed ^ path.getLast().key());
                List<Edge> complete = new ArrayList<>();
                for (int i = 1; i < path.size(); i++) {
                    Point from = path.get(i - 1);
                    Point to = path.get(i);
                    Terrain start = query.terrain(from);
                    Terrain end = query.terrain(to);
                    int area = query.area(from, 4);
                    int radius = Math.min(settings.maximumRadius(), 1 + (int) Math.sqrt(area / 3.0));
                    complete.add(new Edge(mix(seed ^ from.key()), basin, from.worldX(), from.worldZ(), start.height() - 1,
                            to.worldX(), to.worldZ(), end.height() - (end.water() ? 0 : 1), area, radius, end.water()));
                }
                if (complete.stream().noneMatch(edge -> intersects(edge, minimumX, minimumZ, size))
                        || !validator.admit(List.copyOf(complete))) continue;
                routes++;
                for (int i = 1; i < path.size(); i++) {
                    Point from = path.get(i - 1);
                    Point to = path.get(i);
                    int radius = complete.get(i - 1).radius();
                    MutableEdge edge = edges.computeIfAbsent(from.key(), ignored ->
                            new MutableEdge(from, to, basin, radius));
                    edge.sources++;
                }
            }
            List<Edge> result = new ArrayList<>();
            for (var value : edges.values()) {
                Terrain source = query.terrain(value.source);
                Terrain target = query.terrain(value.target);
                int radius = value.radius;
                int sx = value.source.worldX();
                int sz = value.source.worldZ();
                int tx = value.target.worldX();
                int tz = value.target.worldZ();
                // Includes bounded bending and the neighbor stage certificate.
                int reach = radius + 5;
                if (Math.max(sx, tx) + reach < minimumX - 1 || Math.min(sx, tx) - reach > minimumX + size
                        || Math.max(sz, tz) + reach < minimumZ - 1 || Math.min(sz, tz) - reach > minimumZ + size) continue;
                result.add(new Edge(mix(seed ^ value.source.key()), value.basin, sx, sz, source.height() - 1,
                        tx, tz, target.height() - (target.water() ? 0 : 1), value.sources, radius, target.water()));
            }
            result.sort(Comparator.comparingLong(Edge::id));
            return new Plan(result, routes, query.samples.size(), false);
        } catch (WorkLimit ignored) {
            // Work exhaustion never leaves a truncated collection of channels.
            return new Plan(List.of(), 0, query.samples.size(), true);
        }
    }

    private static boolean intersects(Edge edge, int minimumX, int minimumZ, int size) {
        int reach = edge.radius() + 5;
        return Math.max(edge.sourceX(), edge.targetX()) + reach >= minimumX - 1
                && Math.min(edge.sourceX(), edge.targetX()) - reach <= minimumX + size
                && Math.max(edge.sourceZ(), edge.targetZ()) + reach >= minimumZ - 1
                && Math.min(edge.sourceZ(), edge.targetZ()) - reach <= minimumZ + size;
    }

    public record Settings(int maximumSteps, double frequency, int minimumArea, int maximumRadius, int maximumSamples) {
        public Settings {
            if (maximumSteps < 2 || maximumSteps > 12 || !Double.isFinite(frequency) || frequency < 0 || frequency > 1
                    || minimumArea < 1 || minimumArea > 64 || maximumRadius < 1 || maximumRadius > 8
                    || maximumSamples < 1 || maximumSamples > 4096) throw new IllegalArgumentException("Unbounded connected river settings");
        }
    }

    /** Ground or existing free surface; rainfall is a climate propensity, never spendable water. */
    public record Terrain(int height, boolean water, float rainfall) {
        public Terrain {
            if (!Float.isFinite(rainfall) || rainfall < 0 || rainfall > 1 || height < -2048 || height > 2047)
                throw new IllegalArgumentException("Invalid predicted terrain");
        }
    }
    @FunctionalInterface public interface TerrainSampler { Terrain sample(int worldX, int worldZ); }
    @FunctionalInterface public interface RouteValidator { boolean admit(List<Edge> completeRoute); }

    /** Global endpoints and widths are independent of the requesting chunk. */
    public record Edge(long id, long basinId, int sourceX, int sourceZ, int sourceY,
                       int targetX, int targetZ, int targetY, int contributingSources, int radius, boolean receiving) { }
    public record Plan(List<Edge> edges, int certifiedRoutes, int terrainSamples, boolean budgetExceeded) {
        public Plan { edges = List.copyOf(edges); }
    }

    private static final class Query {
        final Settings settings;
        final TerrainSampler sampler;
        final Map<Long, Terrain> samples = new HashMap<>();
        final Map<Long, Point> downstream = new HashMap<>();
        final Map<AreaKey, Integer> areas = new HashMap<>();
        Query(Settings settings, TerrainSampler sampler) { this.settings = settings; this.sampler = sampler; }

        Terrain terrain(Point point) {
            if (samples.containsKey(point.key())) return samples.get(point.key());
            if (samples.size() >= settings.maximumSamples()) throw new WorkLimit();
            Terrain terrain = Math.abs((long) point.x * SPACING) > 29_999_968
                    || Math.abs((long) point.z * SPACING) > 29_999_968 ? null : sampler.sample(point.worldX(), point.worldZ());
            samples.put(point.key(), terrain);
            return terrain;
        }

        Point next(Point point) {
            if (downstream.containsKey(point.key())) return downstream.get(point.key());
            Terrain home = terrain(point);
            Point best = null;
            double steepest = 0;
            boolean unknown = home == null;
            if (home != null && !home.water()) {
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    Point neighbor = new Point(point.x + dx, point.z + dz);
                    Terrain other = terrain(neighbor);
                    if (other == null) { unknown = true; continue; }
                    double slope = (home.height() - other.height()) / Math.hypot(dx, dz);
                    if (slope > steepest || slope > 0 && slope == steepest
                            && (best == null || neighbor.key() < best.key())) { best = neighbor; steepest = slope; }
                }
            }
            if (unknown) best = null;
            downstream.put(point.key(), best);
            return best;
        }

        int area(Point point, int depth) {
            if (depth == 0) return 1;
            AreaKey key = new AreaKey(point.key(), depth);
            Integer cached = areas.get(key);
            if (cached != null) return cached;
            int area = 1;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                Point neighbor = new Point(point.x + dx, point.z + dz);
                if (point.equals(next(neighbor))) area = Math.min(64, area + area(neighbor, depth - 1));
            }
            areas.put(key, area);
            return area;
        }

        List<Point> route(Point source) {
            List<Point> points = new ArrayList<>();
            Point current = source;
            for (int step = 0; step <= settings.maximumSteps(); step++) {
                Terrain terrain = terrain(current);
                if (terrain == null) return List.of();
                points.add(current);
                if (terrain.water()) return points.size() > 1 ? points : List.of();
                current = next(current);
                if (current == null) return List.of();
            }
            return List.of();
        }
    }

    private record Point(int x, int z) {
        long key() { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
        int worldX() { return Math.toIntExact((long) x * SPACING + SPACING / 2); }
        int worldZ() { return Math.toIntExact((long) z * SPACING + SPACING / 2); }
    }
    private record AreaKey(long cell, int depth) { }
    private static final class MutableEdge {
        final Point source;
        final Point target;
        final long basin;
        final int radius;
        int sources;
        MutableEdge(Point source, Point target, long basin, int radius) {
            this.source = source; this.target = target; this.basin = basin; this.radius = radius;
        }
    }
    private static final class WorkLimit extends RuntimeException {
        WorkLimit() { super(null, null, false, false); }
    }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
