package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;

import java.util.Arrays;

/**
 * Compact four-by-four drainage topology for one chunk.
 *
 * <p>Each cell stores a four-bit direction and four-bit contributing-area
 * estimate. This is deliberately coarser than block terrain: it can represent
 * tributaries and confluences without creating per-block hydrology state.</p>
 */
public record WatershedDrainageGrid(long directionBits, long accumulationBits) {

    public static final int GRID_SIZE = 4;
    public static final int CELL_COUNT = GRID_SIZE * GRID_SIZE;

    /** Creates a deterministic grid from the sixteen sampled surface heights. */
    public static WatershedDrainageGrid fromHeights(int[] heights, DrainageDirection boundaryDirection) {
        if (heights == null || heights.length != CELL_COUNT) {
            throw new IllegalArgumentException("A four-by-four drainage grid requires sixteen heights");
        }
        DrainageDirection fallback = boundaryDirection == null ? DrainageDirection.SINK : boundaryDirection;
        DrainageDirection[] directions = new DrainageDirection[CELL_COUNT];
        int[] destination = new int[CELL_COUNT];
        Arrays.fill(destination, -1);

        for (int cell = 0; cell < CELL_COUNT; cell++) {
            int x = cell & 3;
            int z = cell >>> 2;
            int best = -1;
            int bestHeight = heights[cell];
            for (DrainageDirection candidate : DrainageDirection.values()) {
                if (candidate == DrainageDirection.SINK) {
                    continue;
                }
                int nextX = x + candidate.stepX();
                int nextZ = z + candidate.stepZ();
                if (nextX < 0 || nextX >= GRID_SIZE || nextZ < 0 || nextZ >= GRID_SIZE) {
                    continue;
                }
                int next = nextZ * GRID_SIZE + nextX;
                int nextHeight = heights[next];
                if (nextHeight < bestHeight || nextHeight == bestHeight && next < best) {
                    best = next;
                    bestHeight = nextHeight;
                }
            }
            if (best >= 0) {
                destination[cell] = best;
                directions[cell] = directionBetween(cell, best);
            } else if (pointsOutside(x, z, fallback)) {
                directions[cell] = fallback;
            } else {
                directions[cell] = DrainageDirection.SINK;
            }
        }

        Integer[] order = new Integer[CELL_COUNT];
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            order[cell] = cell;
        }
        Arrays.sort(order, (left, right) -> {
            int heightOrder = Integer.compare(heights[right], heights[left]);
            return heightOrder != 0 ? heightOrder : Integer.compare(left, right);
        });
        int[] accumulation = new int[CELL_COUNT];
        Arrays.fill(accumulation, 1);
        for (int cell : order) {
            int next = destination[cell];
            if (next >= 0) {
                accumulation[next] = Math.min(15, accumulation[next] + accumulation[cell]);
            }
        }

        long packedDirections = 0L;
        long packedAccumulation = 0L;
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            packedDirections |= (long) directions[cell].ordinal() << (cell * 4);
            packedAccumulation |= (long) accumulation[cell] << (cell * 4);
        }
        return new WatershedDrainageGrid(packedDirections, packedAccumulation);
    }

    /** Creates a migration-safe uniform grid for version-one save entries. */
    public static WatershedDrainageGrid uniform(DrainageDirection direction) {
        DrainageDirection safe = direction == null ? DrainageDirection.SINK : direction;
        long directions = 0L;
        long accumulation = 0L;
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            directions |= (long) safe.ordinal() << (cell * 4);
            accumulation |= 1L << (cell * 4);
        }
        return new WatershedDrainageGrid(directions, accumulation);
    }

    /** Returns the local cell for a block coordinate inside its chunk. */
    public static int cell(int blockX, int blockZ) {
        return ((blockZ & 15) >>> 2) * GRID_SIZE + ((blockX & 15) >>> 2);
    }

    /** Returns the local flow direction for a cell. */
    public DrainageDirection direction(int cell) {
        return DrainageDirection.fromId(nibble(directionBits, cell));
    }

    /** Returns the clamped contributing-cell count from one through fifteen. */
    public int accumulation(int cell) {
        return Math.max(1, nibble(accumulationBits, cell));
    }

    /** Returns whether this cell represents a compact tributary confluence. */
    public boolean confluence(int cell) {
        return accumulation(cell) >= 4;
    }

    private static int nibble(long bits, int cell) {
        int safeCell = Math.max(0, Math.min(CELL_COUNT - 1, cell));
        return (int) ((bits >>> (safeCell * 4)) & 0xFL);
    }

    private static boolean pointsOutside(int x, int z, DrainageDirection direction) {
        return direction != DrainageDirection.SINK
                && (x + direction.stepX() < 0 || x + direction.stepX() >= GRID_SIZE
                || z + direction.stepZ() < 0 || z + direction.stepZ() >= GRID_SIZE);
    }

    private static DrainageDirection directionBetween(int source, int destination) {
        int sourceX = source & 3;
        int sourceZ = source >>> 2;
        int destinationX = destination & 3;
        int destinationZ = destination >>> 2;
        for (DrainageDirection direction : DrainageDirection.values()) {
            if (direction.stepX() == Integer.signum(destinationX - sourceX)
                    && direction.stepZ() == Integer.signum(destinationZ - sourceZ)) {
                return direction;
            }
        }
        return DrainageDirection.SINK;
    }
}
