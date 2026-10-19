package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RiverHydraulics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure globally keyed channel geometry, clipped only after an overlapping halo is resolved. */
public final class RiverChannelShape {
    private RiverChannelShape() { }

    public static Shape raster(List<RiverConnectedPlanner.Edge> edges, int minimumX, int minimumZ,
                               int size, int depth, int maximumDrop, boolean waterfalls) {
        return raster(edges, minimumX, minimumZ, size, depth, maximumDrop, waterfalls, null);
    }

    /** Fine shoreline samples share the caller's complete-plan prediction budget. */
    public static Shape raster(List<RiverConnectedPlanner.Edge> edges, int minimumX, int minimumZ,
                               int size, int depth, int maximumDrop, boolean waterfalls,
                               RiverConnectedPlanner.TerrainSampler terrain) {
        return raster(edges, minimumX, minimumZ, size, depth, maximumDrop, waterfalls, terrain, false, new RasterBudget(131_072));
    }

    /** Complete source geometry for one admission decision, independent of query bounds. */
    public static Shape rasterComplete(List<RiverConnectedPlanner.Edge> route, int depth, int maximumDrop,
                                       boolean waterfalls, RiverConnectedPlanner.TerrainSampler terrain) {
        return rasterComplete(route, depth, maximumDrop, waterfalls, terrain, new RasterBudget(131_072));
    }

    public static Shape rasterComplete(List<RiverConnectedPlanner.Edge> route, int depth, int maximumDrop,
                                       boolean waterfalls, RiverConnectedPlanner.TerrainSampler terrain, RasterBudget budget) {
        return raster(route, 0, 0, 32, depth, maximumDrop, waterfalls, terrain, true, budget);
    }

    private static Shape raster(List<RiverConnectedPlanner.Edge> edges, int minimumX, int minimumZ,
                                int size, int depth, int maximumDrop, boolean waterfalls,
                                RiverConnectedPlanner.TerrainSampler terrain, boolean complete, RasterBudget budget) {
        if (size < 16 || size > 32 || depth < 1 || depth > 3 || maximumDrop < 1 || maximumDrop > 16
                || edges.size() > 4096) throw new IllegalArgumentException("Unbounded channel raster");
        Map<Long, Mutable> columns = new HashMap<>();
        for (var edge : edges.stream().sorted(Comparator.comparingLong(RiverConnectedPlanner.Edge::id)).toList()) {
            double dx = (double) edge.targetX() - edge.sourceX();
            double dz = (double) edge.targetZ() - edge.sourceZ();
            double length = Math.hypot(dx, dz);
            if (length < 1 || length > 64 || edge.sourceY() < edge.targetY()) return new Shape(List.of());
            double slope = (edge.sourceY() - edge.targetY()) / length;
            double bend = slope < 0.20 ? ((edge.id() & 1) == 0 ? 1 : -1) * Math.min(2, edge.radius() * 0.45) : 0;
            float speed = (float) RiverHydraulics.evaluate((edge.radius() * 2 + 1) * depth * 16,
                    Math.max(1, edge.contributingSources()) * 16, Math.max(0.001, slope), 0.045, 3).velocity();
            int steps = (int) Math.ceil(length * 3);
            for (int step = 0; step <= steps; step++) {
                double amount = step / (double) steps;
                double offset = Math.sin(Math.PI * amount) * bend;
                double cx = edge.sourceX() + dx * amount - dz / length * offset;
                double cz = edge.sourceZ() + dz * amount + dx / length * offset;
                double tangentX = dx - dz / length * Math.PI * bend * Math.cos(Math.PI * amount);
                double tangentZ = dz + dx / length * Math.PI * bend * Math.cos(Math.PI * amount);
                double tangentLength = Math.hypot(tangentX, tangentZ);
                int waterY = (int) Math.floor(edge.sourceY() + (edge.targetY() - edge.sourceY()) * amount);
                for (int oz = -edge.radius(); oz <= edge.radius(); oz++) for (int ox = -edge.radius(); ox <= edge.radius(); ox++) {
                    if (!budget.visit()) return new Shape(List.of());
                    double distance = Math.hypot(ox, oz);
                    if (distance > edge.radius()) continue;
                    int x = (int) Math.floor(cx + 0.5) + ox;
                    int z = (int) Math.floor(cz + 0.5) + oz;
                    if (!complete && terrain == null && (x < minimumX - 2 || x > minimumX + size + 1
                            || z < minimumZ - 2 || z > minimumZ + size + 1)) continue;
                    int localDepth = Math.max(1, depth + (edge.radius() >= 3 && distance < edge.radius() * 0.55 ? 1 : 0)
                            - (distance > edge.radius() * 0.8 ? 1 : 0));
                    Mutable column = columns.get(key(x, z));
                    if (column == null) {
                        column = new Mutable(x, z, waterY, localDepth, edge.basinId());
                        columns.put(key(x, z), column);
                        if (columns.size() > 16_384) return new Shape(List.of());
                    }
                    column.waterY = Math.min(column.waterY, waterY);
                    column.depth = Math.max(column.depth, localDepth);
                    column.dx += tangentX / tangentLength;
                    column.dz += tangentZ / tangentLength;
                    column.speed = Math.max(column.speed, Math.min(8, speed));
                }
            }
        }
        if (terrain != null) {
            for (var column : columns.values()) {
                column.preFineWaterY = column.waterY;
                var home = terrain.sample(column.x, column.z);
                if (home == null) return new Shape(List.of());
                if (home.water()) {
                    if (home.height() > column.waterY) return new Shape(List.of());
                    column.waterY = home.height();
                } else {
                    for (int[] direction : DIRECTIONS) {
                        var neighbor = terrain.sample(column.x + direction[0], column.z + direction[1]);
                        if (neighbor == null) return new Shape(List.of());
                        if (neighbor.water()) column.waterY = Math.min(column.waterY, neighbor.height());
                    }
                }
            }
            // Reconciliation must not lower a fine inlet and then resume the
            // original higher downstream stage. Check complete edge geometry
            // before clipping, so the same defect is rejected on either side
            // of a tile boundary. Uncertain paths remain untouched.
            for (var column : columns.values()) {
                if (column.waterY >= column.preFineWaterY) continue;
                int dx = (int) Math.signum(column.dx);
                int dz = (int) Math.signum(column.dz);
                var xNext = columns.get(key(column.x + dx, column.z));
                var zNext = columns.get(key(column.x, column.z + dz));
                if (dx != 0 && xNext != null && xNext.waterY > column.waterY
                        || dz != 0 && zNext != null && zNext.waterY > column.waterY) return new Shape(List.of());
            }
        }
        List<Column> result = new ArrayList<>();
        for (var column : columns.values()) {
            int top = column.waterY;
            for (int[] direction : DIRECTIONS) {
                var neighbor = columns.get(key(column.x + direction[0], column.z + direction[1]));
                if (neighbor != null) top = Math.max(top, neighbor.waterY);
            }
            int drop = top - column.waterY;
            if (drop > maximumDrop || drop > 1 && !waterfalls) return new Shape(List.of());
            if (!complete && (column.x < minimumX - 1 || column.x > minimumX + size
                    || column.z < minimumZ - 1 || column.z > minimumZ + size)) continue;
            float length = (float) Math.hypot(column.dx, column.dz);
            result.add(new Column(column.x, column.z, column.waterY, column.waterY - column.depth, top,
                    length > 0 ? (float) column.dx / length : 0, length > 0 ? (float) column.dz / length : 0,
                    column.speed, drop > 1 ? RiverChannelColumn.Kind.WATERFALL : RiverChannelColumn.Kind.STREAM, column.basin));
        }
        result.sort(Comparator.comparingInt(Column::z).thenComparingInt(Column::x));
        return new Shape(result, maximumDrop, waterfalls);
    }

    /**
     * Combines already-certified full source geometries, then clips once.
     * Only minimum-stage inputs direct a lowered confluence; higher inputs
     * cannot point a lower receiving column back toward their higher stage.
     */
    public static Shape mergeCertified(List<Shape> routes, int minimumX, int minimumZ, int size,
                                        int maximumDrop, boolean waterfalls) {
        if (routes.size() > 1024 || size < 16 || size > 32 || maximumDrop < 1 || maximumDrop > 16)
            throw new IllegalArgumentException("Unbounded certified geometry merge");
        var accumulator = new Accumulator();
        for (var route : routes) if (!accumulator.add(route)) return new Shape(List.of());
        return accumulator.finish(minimumX, minimumZ, size, maximumDrop, waterfalls);
    }

    /** Invocation-owned bounded union; full route lists are not retained. */
    public static final class Accumulator {
        private final Map<Long, Merge> columns = new HashMap<>();
        private int visits;
        public boolean add(Shape route) {
            for (var column : route.columns()) {
                if (++visits > 131_072) return false;
                var merged = columns.get(key(column.x(), column.z()));
                if (merged == null) {
                    if (columns.size() >= 16_384) return false;
                    columns.put(key(column.x(), column.z()), new Merge(column));
                } else merged.add(column);
            }
            return true;
        }
        public Shape finish(int minimumX, int minimumZ, int size, int maximumDrop, boolean waterfalls) {
            List<Column> result = new ArrayList<>();
            for (var merged : columns.values()) {
                var column = merged.first;
                int top = merged.top;
                for (int[] direction : DIRECTIONS) {
                    var next = columns.get(key(column.x() + direction[0], column.z() + direction[1]));
                    if (next != null) top = Math.max(top, next.stage);
                }
                int drop = top - merged.stage;
                // Keep the intended column even when combining routes requires an
                // unsupported curtain. Physical slice admission checks this policy;
                // an unrelated conflict must not erase another tile's geometry.
                if (column.x() < minimumX - 1 || column.x() > minimumX + size
                        || column.z() < minimumZ - 1 || column.z() > minimumZ + size) continue;
                double length = Math.hypot(merged.dx, merged.dz);
                result.add(new Column(column.x(), column.z(), merged.stage, merged.floor, top,
                        length > 0 ? (float) (merged.dx / length) : 0, length > 0 ? (float) (merged.dz / length) : 0,
                        merged.speed, drop > 1 ? RiverChannelColumn.Kind.WATERFALL : RiverChannelColumn.Kind.STREAM, merged.basin));
            }
            result.sort(Comparator.comparingInt(Column::z).thenComparingInt(Column::x));
            return new Shape(result, maximumDrop, waterfalls);
        }
    }

    /** Shared logical raster work, including repeat visits to cached terrain. */
    public static final class RasterBudget {
        private final int maximum;
        private int visits;
        private boolean exhausted;
        public RasterBudget(int maximum) {
            if (maximum < 1 || maximum > 1_048_576) throw new IllegalArgumentException("Unbounded raster budget");
            this.maximum = maximum;
        }
        private boolean visit() {
            if (visits >= maximum) { exhausted = true; return false; }
            visits++;
            return true;
        }
        public int visits() { return visits; }
        public boolean exhausted() { return exhausted; }
    }

    private static final class Merge {
        final Column first;
        int stage, floor, top;
        long basin;
        double dx, dz;
        float speed;
        Merge(Column column) {
            first = column; stage = column.waterY(); floor = column.floorY(); top = column.topY();
            basin = column.basinId(); dx = column.directionX(); dz = column.directionZ(); speed = column.speed();
        }
        void add(Column column) {
            floor = Math.min(floor, column.floorY());
            top = Math.max(top, column.topY());
            speed = Math.max(speed, column.speed());
            if (column.waterY() < stage) {
                stage = column.waterY(); basin = column.basinId(); dx = column.directionX(); dz = column.directionZ();
            } else if (column.waterY() == stage) {
                basin = Math.min(basin, column.basinId()); dx += column.directionX(); dz += column.directionZ();
            }
        }
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    public record Column(int x, int z, int waterY, int floorY, int topY, float directionX, float directionZ,
                         float speed, RiverChannelColumn.Kind kind, long basinId) { }
    public record Shape(List<Column> columns, int maximumDrop, boolean waterfalls) {
        public Shape(List<Column> columns) { this(columns, 16, true); }
        public Shape {
            if (maximumDrop < 1 || maximumDrop > 16) throw new IllegalArgumentException("Invalid curtain policy");
            columns = List.copyOf(columns);
        }
        public Column at(int x, int z) {
            for (var column : columns) if (column.x() == x && column.z() == z) return column;
            return null;
        }
    }
    private static final class Mutable {
        final int x, z;
        final long basin;
        int waterY, depth, preFineWaterY;
        double dx, dz;
        float speed;
        Mutable(int x, int z, int waterY, int depth, long basin) {
            this.x = x; this.z = z; this.waterY = waterY; this.depth = depth; this.basin = basin;
        }
    }
}
