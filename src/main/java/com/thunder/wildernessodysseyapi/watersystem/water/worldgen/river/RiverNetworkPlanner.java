package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Pure coarse river topology. Strict downhill D8 routing preserves closed basins;
 * an independent minimax priority flood describes potential spills, never uphill
 * flowing channels. No plan is permission to carve uncertain terrain.
 */
public final class RiverNetworkPlanner {
    private RiverNetworkPlanner() { }

    /** Plans at most 4096 immutable samples, with no cache, executor or live-world access. */
    public static Plan plan(RiverTerrainSnapshot terrain) {
        var layout = terrain.layout();
        int size = layout.width() * layout.height();
        int[] downstream = new int[size];
        Arrays.fill(downstream, -1);
        Terminal[] terminals = new Terminal[size];
        int[] area = new int[size];
        int[] incoming = new int[size];
        List<Integer> order = new ArrayList<>();
        for (int cell = 0; cell < size; cell++) {
            int x = x(layout, cell);
            int z = z(layout, cell);
            var sample = terrain.terrainAt(x, z);
            if (sample == null) continue;
            area[cell] = 1;
            order.add(cell);
            if (sample.receivingWater()) {
                terminals[cell] = Terminal.RECEIVING_WATER;
                continue;
            }
            boolean unknown = false;
            double steepest = 0;
            int best = -1;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                var neighbor = terrain.terrainAt(x + dx, z + dz);
                if (neighbor == null) { unknown = true; continue; }
                double drop = ((double) sample.elevation() - neighbor.elevation()) / Math.hypot(dx, dz);
                int next = index(layout, x + dx, z + dz);
                if (drop > steepest || drop > 0 && drop == steepest
                        && (best < 0 || cellKey(x + dx, z + dz) < cellKey(x(layout, best), z(layout, best)))) {
                    steepest = drop;
                    best = next;
                }
            }
            // An unseen neighbor may have a lower head than the best known one.
            // Do not fabricate an outlet at a planning or unloaded frontier.
            if (unknown) {
                terminals[cell] = Terminal.UNRESOLVED;
            } else if (best >= 0) {
                terminals[cell] = Terminal.DOWNSTREAM;
                downstream[cell] = best;
                incoming[best]++;
            } else {
                terminals[cell] = Terminal.CLOSED_BASIN;
            }
        }
        order.sort(Comparator.<Integer>comparingInt(cell ->
                java.util.Objects.requireNonNull(terrain.terrainAt(x(layout, cell), z(layout, cell))).elevation()).reversed()
                .thenComparingLong(cell -> cellKey(x(layout, cell), z(layout, cell))));
        for (int cell : order) if (downstream[cell] >= 0) area[downstream[cell]] += area[cell];

        Spill[] spills = potentialSpills(terrain, order);
        List<Node> nodes = new ArrayList<>(order.size());
        int unresolved = 0;
        // Spatial order, rather than hash iteration or worker completion order, is public.
        for (int cell = 0; cell < size; cell++) {
            if (terminals[cell] == null) continue;
            int x = x(layout, cell);
            int z = z(layout, cell);
            int target = downstream[cell];
            var sample = java.util.Objects.requireNonNull(terrain.terrainAt(x, z));
            if (terminals[cell] == Terminal.UNRESOLVED) unresolved++;
            nodes.add(new Node(cellKey(x, z), reachId(terrain, x, z), x, z, sample.elevation(),
                    target < 0 ? x : x(layout, target), target < 0 ? z : z(layout, target),
                    terminals[cell], area[cell], false, incoming[cell] >= 2,
                    spills[cell].elevation(), spills[cell].knownWater()));
        }
        return new Plan(layout, List.copyOf(nodes), unresolved);
    }

    private static Spill[] potentialSpills(RiverTerrainSnapshot terrain, List<Integer> known) {
        var layout = terrain.layout();
        Spill[] best = new Spill[layout.width() * layout.height()];
        PriorityQueue<Spill> queue = new PriorityQueue<>(Comparator.comparingInt(Spill::elevation)
                .thenComparing(spill -> !spill.knownWater()).thenComparingInt(Spill::cell));
        for (int cell : known) {
            int x = x(layout, cell);
            int z = z(layout, cell);
            var sample = java.util.Objects.requireNonNull(terrain.terrainAt(x, z));
            boolean frontier = false;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (terrain.terrainAt(x + dx, z + dz) == null) frontier = true;
            }
            if (frontier || sample.receivingWater()) {
                best[cell] = new Spill(cell, sample.elevation(), sample.receivingWater());
                queue.add(best[cell]);
            }
        }
        while (!queue.isEmpty()) {
            Spill spill = queue.remove();
            if (best[spill.cell()] != spill) continue;
            int x = x(layout, spill.cell());
            int z = z(layout, spill.cell());
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                var sample = terrain.terrainAt(x + dx, z + dz);
                if (sample == null) continue;
                int next = index(layout, x + dx, z + dz);
                int elevation = Math.max(spill.elevation(), sample.elevation());
                Spill previous = best[next];
                if (previous == null || elevation < previous.elevation()
                        || elevation == previous.elevation() && spill.knownWater() && !previous.knownWater()) {
                    best[next] = new Spill(next, elevation, spill.knownWater());
                    queue.add(best[next]);
                }
            }
        }
        return best;
    }

    private static int x(RiverTerrainSnapshot.Layout layout, int cell) {
        return layout.minimumCellX() + cell % layout.width();
    }

    private static int z(RiverTerrainSnapshot.Layout layout, int cell) {
        return layout.minimumCellZ() + cell / layout.width();
    }

    private static int index(RiverTerrainSnapshot.Layout layout, int x, int z) {
        return (z - layout.minimumCellZ()) * layout.width() + x - layout.minimumCellX();
    }

    private static long cellKey(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    private static long reachId(RiverTerrainSnapshot terrain, int x, int z) {
        var key = terrain.key();
        long value = key.worldSeed() ^ key.terrainIdentity() ^ key.dimension().hashCode()
                ^ cellKey(x, z) * 0x9E3779B97F4A7C15L
                ^ (long) terrain.layout().cellSize() * 0x165667B19E3779F9L
                ^ RiverTerrainSnapshot.PLANNER_VERSION;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** Only DOWNSTREAM has a flowing edge. Other terminal kinds do not invent connections. */
    public enum Terminal { DOWNSTREAM, CLOSED_BASIN, RECEIVING_WATER, UNRESOLVED }

    /** Immutable coarse reach; contributing cells are a lower bound, never a physical inventory. */
    public record Node(long cellKey, long reachId, int cellX, int cellZ, int elevation,
                       int downstreamX, int downstreamZ, Terminal terminal, int contributingCells,
                       boolean catchmentComplete, boolean confluence, int potentialSpillElevation,
                       boolean spillReachesKnownWater) { }

    /** No mutable arrays or world references escape the planner. */
    public record Plan(RiverTerrainSnapshot.Layout layout, List<Node> nodes, int unresolvedCells) {
        public Plan { nodes = List.copyOf(nodes); }

        public Node nodeAt(int cellX, int cellZ) {
            for (Node node : nodes) if (node.cellX() == cellX && node.cellZ() == cellZ) return node;
            return null;
        }
    }

    private record Spill(int cell, int elevation, boolean knownWater) { }
}
