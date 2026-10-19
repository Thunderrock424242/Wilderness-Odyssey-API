package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RiverHydraulics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plans one complete bounded catchment against captured CARVERS terrain. Every
 * stream reaches existing water or a sealed pool; unresolved drainage is left
 * intact. No block writes, live terrain references or shared mutable caches.
 */
public final class RiverCatchmentPlanner {
    private static final int SIZE = RiverTileTerrain.SIZE;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private RiverCatchmentPlanner() { }

    public static Plan plan(long seed, int minimumX, int minimumZ, RiverTileTerrain terrain,
                            RiverGenerationSettings settings) {
        long identity = mix(seed ^ ((long) minimumX << 32) ^ minimumZ ^ settings.fingerprint());
        if (!settings.enabled() || (identity >>> 11) * 0x1.0p-53 >= settings.frequency()) return Plan.EMPTY;
        var layout = new RiverTerrainSnapshot.Layout(Math.floorDiv(minimumX, 4),
                Math.floorDiv(minimumZ, 4), 8, 8, 4, 64);
        var snapshot = RiverTerrainSnapshot.capture(
                new RiverTerrainSnapshot.Key(seed, "minecraft:overworld", settings.fingerprint()), layout,
                (x, z) -> new RiverTerrainSnapshot.Terrain(
                        terrain.height(x - minimumX, z - minimumZ), terrain.water(x - minimumX, z - minimumZ)));
        var network = RiverNetworkPlanner.plan(snapshot);
        var targets = network.nodes().stream().filter(node ->
                        node.terminal() == RiverNetworkPlanner.Terminal.RECEIVING_WATER
                                || settings.lakes() && node.terminal() == RiverNetworkPlanner.Terminal.CLOSED_BASIN)
                .sorted(Comparator.comparing((RiverNetworkPlanner.Node node) ->
                                node.terminal() != RiverNetworkPlanner.Terminal.RECEIVING_WATER)
                        .thenComparingInt(RiverNetworkPlanner.Node::elevation)
                        .thenComparingLong(RiverNetworkPlanner.Node::cellKey)).limit(16).toList();
        for (var target : targets) {
            int targetX = layout.worldX(target.cellX()) - minimumX;
            int targetZ = layout.worldZ(target.cellZ()) - minimumZ;
            boolean receiving = terrain.water(targetX, targetZ);
            int waterY = target.elevation() + (receiving ? 0 : settings.lakeDepth() - 1);
            boolean[] lake = flood(terrain, targetX, targetZ, waterY, receiving, settings.maximumLakeColumns());
            if (lake == null) continue;
            MutableColumn[] columns = new MutableColumn[SIZE * SIZE];
            {
                for (int cell = 0; cell < lake.length; cell++) if (lake[cell] && !terrain.water(cell % SIZE, cell / SIZE)
                        && cell % SIZE > 0 && cell % SIZE < 31 && cell / SIZE > 0 && cell / SIZE < 31) {
                    columns[cell] = new MutableColumn(waterY, waterY - (receiving ? settings.streamDepth() : settings.lakeDepth()),
                            RiverChannelColumn.Kind.LAKE);
                }
            }
            for (var source : network.nodes()) {
                if (source.contributingCells() < settings.minimumContributingCells()
                        || source.elevation() <= waterY + 1) continue;
                var route = route(network, source, layout, minimumX, minimumZ, lake);
                if (route.size() < 2) continue;
                for (int edge = 1; edge < route.size(); edge++) {
                    raster(columns, terrain, lake, layout, minimumX, minimumZ,
                            route.get(edge - 1), route.get(edge), waterY, settings);
                }
            }
            connectReceivingMargins(columns, terrain, lake, waterY, settings);
            if (!addDrops(columns, settings)) continue;
            int writes = validate(columns, terrain, settings);
            if (writes < 1 || writes > settings.maximumWrites()) continue;
            List<RiverChannelColumn> result = new ArrayList<>();
            for (int cell = 0; cell < columns.length; cell++) {
                MutableColumn column = columns[cell];
                if (column == null) continue;
                float length = (float) Math.hypot(column.x, column.z);
                result.add(new RiverChannelColumn(cell % SIZE, cell / SIZE, column.waterY, column.floorY,
                        column.topY, length > 0 ? column.x / length : 0,
                        length > 0 ? column.z / length : 0, column.speed, column.kind));
            }
            return new Plan(mix(identity ^ target.cellKey()), result, writes);
        }
        return Plan.EMPTY;
    }

    private static boolean[] flood(RiverTileTerrain terrain, int startX, int startZ, int waterY,
                                   boolean receiving, int maximumColumns) {
        boolean[] visited = new boolean[SIZE * SIZE];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startX + startZ * SIZE);
        visited[startX + startZ * SIZE] = true;
        int count = 0;
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int x = cell % SIZE;
            int z = cell / SIZE;
            if (!receiving && (x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1)) return null;
            if (!terrain.water(x, z) && ++count > maximumColumns) return null;
            for (int[] direction : CARDINALS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (!inside(nx, nz) || terrain.height(nx, nz) > waterY) continue;
                if (terrain.water(nx, nz) && terrain.height(nx, nz) != waterY) return null;
                int next = nx + nz * SIZE;
                if (!visited[next]) { visited[next] = true; queue.add(next); }
            }
        }
        return visited;
    }

    private static List<RiverNetworkPlanner.Node> route(RiverNetworkPlanner.Plan network,
            RiverNetworkPlanner.Node source, RiverTerrainSnapshot.Layout layout,
            int minimumX, int minimumZ, boolean[] lake) {
        List<RiverNetworkPlanner.Node> path = new ArrayList<>();
        var current = source;
        for (int steps = 0; steps < 64 && current != null; steps++) {
            path.add(current);
            int x = layout.worldX(current.cellX()) - minimumX;
            int z = layout.worldZ(current.cellZ()) - minimumZ;
            if (lake[x + z * SIZE]) return path;
            if (current.terminal() != RiverNetworkPlanner.Terminal.DOWNSTREAM) break;
            current = network.nodeAt(current.downstreamX(), current.downstreamZ());
        }
        return List.of();
    }

    private static void raster(MutableColumn[] columns, RiverTileTerrain terrain, boolean[] lake,
            RiverTerrainSnapshot.Layout layout, int minimumX, int minimumZ,
            RiverNetworkPlanner.Node source, RiverNetworkPlanner.Node target, int lakeY,
            RiverGenerationSettings settings) {
        int sx = layout.worldX(source.cellX()) - minimumX;
        int sz = layout.worldZ(source.cellZ()) - minimumZ;
        int tx = layout.worldX(target.cellX()) - minimumX;
        int tz = layout.worldZ(target.cellZ()) - minimumZ;
        double length = Math.hypot(tx - sx, tz - sz);
        int steps = (int) Math.ceil(length * 2);
        float dx = (float) ((tx - sx) / length);
        float dz = (float) ((tz - sz) / length);
        float speed = (float) RiverHydraulics.evaluate(settings.streamDepth() * 3.0 * 16,
                source.contributingCells() * 16.0, Math.max(0.001, (source.elevation() - target.elevation()) / length),
                0.045, 2).velocity();
        for (int step = 0; step <= steps; step++) {
            double amount = step / (double) steps;
            int cx = (int) Math.floor(sx + 0.5 + (tx - sx) * amount);
            int cz = (int) Math.floor(sz + 0.5 + (tz - sz) * amount);
            int y = Math.max(lakeY, (int) Math.floor(source.elevation() - 1
                    + (target.elevation() - source.elevation()) * amount));
            for (int oz = -1; oz <= 1; oz++) for (int ox = -1; ox <= 1; ox++) {
                if (ox * ox + oz * oz > 1) continue;
                int x = cx + ox;
                int z = cz + oz;
                if (!inside(x, z) || x == 0 || z == 0 || x == 31 || z == 31) continue;
                int cell = x + z * SIZE;
                if (lake[cell] || terrain.water(x, z)) continue;
                MutableColumn column = columns[cell];
                if (column == null) {
                    column = new MutableColumn(y, y - settings.streamDepth(), RiverChannelColumn.Kind.STREAM);
                    columns[cell] = column;
                } else {
                    column.waterY = Math.min(column.waterY, y);
                    column.floorY = column.waterY - settings.streamDepth();
                    column.topY = column.waterY;
                }
                column.x += dx;
                column.z += dz;
                column.speed = Math.max(column.speed, speed);
            }
        }
    }

    private static boolean addDrops(MutableColumn[] columns, RiverGenerationSettings settings) {
        for (int cell = 0; cell < columns.length; cell++) {
            MutableColumn column = columns[cell];
            if (column == null) continue;
            int x = cell % SIZE;
            int z = cell / SIZE;
            for (int[] direction : CARDINALS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (!inside(nx, nz)) continue;
                MutableColumn neighbor = columns[nx + nz * SIZE];
                if (neighbor == null || neighbor.waterY <= column.waterY) continue;
                int drop = neighbor.waterY - column.waterY;
                if (drop > 4 || drop > 1 && !settings.waterfalls()) return false;
                column.topY = Math.max(column.topY, neighbor.waterY);
                if (drop > 1) column.kind = RiverChannelColumn.Kind.WATERFALL;
            }
        }
        return true;
    }

    private static void connectReceivingMargins(MutableColumn[] columns, RiverTileTerrain terrain,
            boolean[] lake, int receivingY, RiverGenerationSettings settings) {
        for (int cell = 0; cell < columns.length; cell++) {
            MutableColumn column = columns[cell];
            if (column == null || column.kind == RiverChannelColumn.Kind.LAKE) continue;
            int x = cell % SIZE;
            int z = cell / SIZE;
            for (int[] direction : CARDINALS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (inside(nx, nz) && lake[nx + nz * SIZE] && terrain.water(nx, nz)
                        && terrain.height(nx, nz) == receivingY) {
                    // The exact shoreline can precede its coarse receiver cell.
                    // End at that real surface, then certify the complete drop.
                    column.waterY = receivingY;
                    column.floorY = receivingY - settings.streamDepth();
                    column.topY = receivingY;
                }
            }
        }
    }

    private static int validate(MutableColumn[] columns, RiverTileTerrain terrain, RiverGenerationSettings settings) {
        int writes = 0;
        for (int cell = 0; cell < columns.length; cell++) {
            MutableColumn column = columns[cell];
            if (column == null) continue;
            int x = cell % SIZE;
            int z = cell / SIZE;
            int ground = terrain.height(x, z);
            if (ground - column.floorY > settings.maximumCut()
                    || !terrain.naturalAt(x, column.floorY, z)
                    || !terrain.naturalAt(x, column.floorY - 1, z)) return -1;
            for (int y = column.floorY; y <= ground; y++) if (!terrain.naturalAt(x, y, z)) return -1;
            for (int[] direction : CARDINALS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (!inside(nx, nz)) return -1;
                MutableColumn neighbor = columns[nx + nz * SIZE];
                if (neighbor == null && terrain.water(nx, nz) && terrain.height(nx, nz) != column.waterY) return -1;
                for (int y = column.floorY + 1; y <= column.waterY; y++) {
                    if (neighbor != null && y > neighbor.floorY && y <= neighbor.topY) continue;
                    if (terrain.water(nx, nz) && terrain.height(nx, nz) >= y) continue;
                    if (!terrain.naturalAt(nx, y, nz)) return -1;
                }
            }
            writes += Math.max(ground, column.topY) - column.floorY + 1;
        }
        return writes;
    }

    private static boolean inside(int x, int z) { return x >= 0 && z >= 0 && x < SIZE && z < SIZE; }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record Plan(long basinId, List<RiverChannelColumn> columns, int requiredWrites) {
        public static final Plan EMPTY = new Plan(0, List.of(), 0);
        public Plan { columns = List.copyOf(columns); }
    }

    private static final class MutableColumn {
        int waterY;
        int floorY;
        int topY;
        float x;
        float z;
        float speed;
        RiverChannelColumn.Kind kind;
        MutableColumn(int waterY, int floorY, RiverChannelColumn.Kind kind) {
            this.waterY = waterY; this.floorY = floorY; this.topY = waterY; this.kind = kind;
        }
    }
}
