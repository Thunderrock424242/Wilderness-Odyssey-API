package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Incremental priority-flood D8 topology over cached 16 m DEM cells. Ocean roots
 * export to a named boundary. Unknown-frontier roots retain water in closed
 * lakes until terrain is discovered; no neighbor lookup loads a world chunk.
 */
public final class RegionalDrainageGraph {
    private final Map<Long, RegionalHydrologyState> states;
    private final List<Long> keys;
    private final Map<Long, Node> result = new HashMap<>();
    private final Set<Long> visited = new HashSet<>();
    private final List<Long> order = new ArrayList<>();
    private final PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator
            .comparingDouble(Node::spill).thenComparingLong(Node::key));
    private int seedCursor;
    private int accumulationCursor = -1;
    private int commitCursor;
    private boolean routed;
    private boolean accumulated;

    public RegionalDrainageGraph(Map<Long, RegionalHydrologyState> states) {
        this.states = states;
        this.keys = new ArrayList<>(states.keySet());
        keys.sort(Long::compare);
    }

    /** Returns true only after all three bounded passes have completed. */
    public boolean advance(int budget) {
        while (budget-- > 0) {
            if (seedCursor < keys.size()) {
                long key = keys.get(seedCursor++);
                RegionalHydrologyState state = states.get(key);
                if (state.ocean || frontierCell(key)) {
                    Node root = new Node(key, state.ocean ? RegionalHydrologyState.NO_OUTLET : lowerFrontier(state), key,
                            state.elevation, RegionalHydrologyState.AREA);
                    result.put(key, root);
                    frontier.add(root);
                    visited.add(key);
                }
            } else if (!routed) {
                Node node = frontier.poll();
                if (node == null) {
                    routed = true;
                    accumulationCursor = order.size() - 1;
                    continue;
                }
                order.add(node.key);
                Node rootParent = result.get(node.downstream);
                if (rootParent != null && rootParent.spill < node.spill) {
                    node = new Node(node.key, node.downstream, rootParent.basin, node.spill, node.area);
                    result.put(node.key, node);
                }
                for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
                    if (x == 0 && z == 0) continue;
                    long neighbor = neighbor(node.key, x, z);
                    RegionalHydrologyState state = states.get(neighbor);
                    if (state == null || !visited.add(neighbor)) continue;
                    Node next = new Node(neighbor, node.key, node.basin,
                            Math.max(node.spill, state.elevation), RegionalHydrologyState.AREA);
                    result.put(neighbor, next);
                    frontier.add(next);
                }
            } else if (!accumulated) {
                if (accumulationCursor < 0) { accumulated = true; continue; }
                Node node = result.get(order.get(accumulationCursor--));
                Node parent = result.get(node.downstream);
                if (parent != null) result.put(parent.key, new Node(parent.key, parent.downstream,
                        parent.basin, parent.spill, parent.area + node.area));
            } else if (commitCursor < keys.size()) {
                long key = keys.get(commitCursor++);
                Node node = result.get(key);
                if (node != null) {
                    RegionalHydrologyState state = states.get(key);
                    state.downstream = node.downstream;
                    state.basin = node.basin;
                    state.spillElevation = node.spill;
                    state.contributingArea = node.area;
                }
            } else return true;
        }
        return commitCursor >= keys.size() && accumulated;
    }

    private boolean frontierCell(long key) {
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            if ((x != 0 || z != 0) && !states.containsKey(neighbor(key, x, z))) return true;
        }
        return false;
    }

    private long lowerFrontier(RegionalHydrologyState state) {
        long best = RegionalHydrologyState.NO_OUTLET;
        double lowest = state.elevation;
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            RegionalHydrologyState neighbor = states.get(neighbor(state.key, x, z));
            if (neighbor != null && neighbor.elevation < lowest && (neighbor.ocean || frontierCell(neighbor.key))) {
                best = neighbor.key;
                lowest = neighbor.elevation;
            }
        }
        return best;
    }

    private static long neighbor(long key, int x, int z) {
        return ChunkPos.asLong(ChunkPos.getX(key) + x, ChunkPos.getZ(key) + z);
    }

    private record Node(long key, long downstream, long basin, double spill, double area) { }
}
