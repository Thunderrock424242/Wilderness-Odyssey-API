package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import java.util.*;

/**
 * SpatialHashGrid
 *
 * A uniform-grid spatial hash that bins particles by their grid cell.
 * Neighbour queries run in O(1) average instead of O(n²).
 *
 * Cell size is set to the SPH smoothing radius so a neighbour query
 * only needs to check the 27 surrounding cells (3³ cube).
 */
public class SpatialHashGrid {

    private final float cellSize;
    private final float invCellSize;

    // Maps cell hash → list of particle indices in that cell
    private final HashMap<Long, List<Integer>> cells = new HashMap<>(512);

    // Used to give each query a unique ID to avoid duplicate results
    private int queryId = 0;

    public SpatialHashGrid(float cellSize) {
        this.cellSize    = cellSize;
        this.invCellSize = 1f / cellSize;
    }

    /** Clear all cells. Call before rebuilding each simulation step. */
    public void clear() {
        cells.clear();
    }

    /** Insert a particle at the given index into its cell. */
    public void insert(int index, float x, float y, float z) {
        long key = cellKey(cellX(x), cellY(y), cellZ(z));
        cells.computeIfAbsent(key, k -> new ArrayList<>(8)).add(index);
    }

    /**
     * Find all particle indices within the smoothing radius of (x,y,z).
     * Results are written into `out`. Returns the count found.
     */
    public int queryNeighbours(List<SPHParticle> particles,
                                float x, float y, float z,
                                float radius, List<Integer> out) {
        out.clear();
        int qid = ++queryId;
        float r2 = radius * radius;

        int cx = cellX(x), cy = cellY(y), cz = cellZ(z);
        int range = (int) Math.ceil(radius * invCellSize);

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    long key = cellKey(cx + dx, cy + dy, cz + dz);
                    List<Integer> bucket = cells.get(key);
                    if (bucket == null) continue;

                    for (int idx : bucket) {
                        SPHParticle p = particles.get(idx);
                        if (p.lastQueryId == qid) continue; // already found
                        float ex = p.position.x - x;
                        float ey = p.position.y - y;
                        float ez = p.position.z - z;
                        if (ex*ex + ey*ey + ez*ez <= r2) {
                            p.lastQueryId = qid;
                            out.add(idx);
                        }
                    }
                }
            }
        }
        return out.size();
    }

    // ---- Helpers ----

    private int cellX(float x) { return (int) Math.floor(x * invCellSize); }
    private int cellY(float y) { return (int) Math.floor(y * invCellSize); }
    private int cellZ(float z) { return (int) Math.floor(z * invCellSize); }

    /** Cantor-style hash combining three integers into a long. */
    private long cellKey(int cx, int cy, int cz) {
        // Use prime multipliers to reduce collisions
        return ((long)(cx * 92837111)) ^ ((long)(cy * 689287499)) ^ ((long)(cz * 283923481));
    }
}
