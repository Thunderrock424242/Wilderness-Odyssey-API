package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import java.util.*;

/**
 * A zero-allocation Spatial Hash Grid used to accelerate 3D neighbor searches for particles.
 * <p>
 * Instead of checking every particle against every other particle (O(n²)), the grid divides
 * the world into uniform cells. When looking for neighbors, a particle only checks its own cell
 * and the 26 immediately surrounding cells (a 3x3x3 cube), reducing the lookup time to O(1) average.
 * <p>
 * <b>Performance Note:</b> This class uses strict Object Pooling for its inner lists to prevent
 * massive Garbage Collection (GC) lag spikes during high-frequency physics ticks.
 */
public class SpatialHashGrid {

    private final float cellSize;
    private final float invCellSize;

    /** Active cells containing particle indices for the current physics step. */
    private final HashMap<Long, List<Integer>> cells = new HashMap<>(512);

    /** Object pool used to recycle lists and completely eliminate memory allocations during ticks. */
    private final List<List<Integer>> listPool = new ArrayList<>(512);
    private int poolIndex = 0;

    /** A unique ID incremented per query to prevent the same particle from being added to the results twice. */
    private int queryId = 0;

    /**
     * Constructs a new SpatialHashGrid.
     *
     * @param cellSize The length of one side of a grid cell. This should optimally match the SPH smoothing radius.
     */
    public SpatialHashGrid(float cellSize) {
        this.cellSize = cellSize;
        this.invCellSize = 1f / cellSize;
    }

    /**
     * Clears the grid for the next physics step.
     * <p>
     * Instead of discarding the active lists (which would cause GC pressure), they are returned
     * to the {@link #listPool} to be reused in the next step.
     */
    public void clear() {
        for (List<Integer> list : cells.values()) {
            list.clear();
            if (poolIndex == listPool.size()) {
                listPool.add(list);
            } else {
                listPool.set(poolIndex, list);
            }
            poolIndex++;
        }
        cells.clear();
        poolIndex = 0;
    }

    /**
     * Inserts a particle into the appropriate grid cell based on its 3D world coordinates.
     *
     * @param index The index of the particle in the main simulator particle list.
     * @param x     The x-coordinate of the particle.
     * @param y     The y-coordinate of the particle.
     * @param z     The z-coordinate of the particle.
     */
    public void insert(int index, float x, float y, float z) {
        long key = cellKey(cellX(x), cellY(y), cellZ(z));
        List<Integer> bucket = cells.get(key);

        if (bucket == null) {
            // Retrieve a list from the pool instead of creating a new one
            if (poolIndex > 0) {
                poolIndex--;
                bucket = listPool.get(poolIndex);
            } else {
                bucket = new ArrayList<>(8);
            }
            cells.put(key, bucket);
        }
        bucket.add(index);
    }

    /**
     * Finds all particles within a given radius of a target position.
     *
     * @param particles The main list of particles from the simulator.
     * @param x         The center x-coordinate of the search sphere.
     * @param y         The center y-coordinate of the search sphere.
     * @param z         The center z-coordinate of the search sphere.
     * @param radius    The search radius (typically the SPH smoothing radius).
     * @param out       A pre-allocated list where the indices of found neighbors will be stored.
     * @return The number of neighboring particles found.
     */
    public int queryNeighbours(List<SPHParticle> particles, float x, float y, float z, float radius, List<Integer> out) {
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
                        if (p.lastQueryId == qid) continue;
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

    private int cellX(float x) { return (int) Math.floor(x * invCellSize); }
    private int cellY(float y) { return (int) Math.floor(y * invCellSize); }
    private int cellZ(float z) { return (int) Math.floor(z * invCellSize); }

    /**
     * Combines 3D grid coordinates into a single unique long integer using Cantor-style hashing
     * with prime multipliers to minimize cell collisions.
     */
    private long cellKey(int cx, int cy, int cz) {
        return ((long)(cx * 92837111)) ^ ((long)(cy * 689287499)) ^ ((long)(cz * 283923481));
    }
}