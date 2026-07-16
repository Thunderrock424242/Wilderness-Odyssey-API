package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Integer coordinate of a large horizontal atmospheric cell.
 *
 * <p>Mapping uses floor division so negative block coordinates are assigned to
 * the same-size cells as positive coordinates. The packed form preserves both
 * signed coordinates without allocating an object in storage or map keys.</p>
 *
 * @param x atmospheric cell X
 * @param z atmospheric cell Z
 */
public record AtmosphereCellKey(int x, int z) {

    /** Maps a block position to its containing atmospheric cell. */
    public static AtmosphereCellKey fromBlock(int blockX, int blockZ, int cellSize) {
        validateCellSize(cellSize);
        return new AtmosphereCellKey(Math.floorDiv(blockX, cellSize), Math.floorDiv(blockZ, cellSize));
    }

    /** Restores a key previously returned by {@link #packed()}. */
    public static AtmosphereCellKey fromPacked(long packed) {
        return new AtmosphereCellKey((int) (packed >> 32), (int) packed);
    }

    /** Packs both signed cell coordinates into one stable long. */
    public long packed() {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** Returns the block X nearest this cell's geometric center. */
    public int centerBlockX(int cellSize) {
        return centerCoordinate(x, cellSize);
    }

    /** Returns the block Z nearest this cell's geometric center. */
    public int centerBlockZ(int cellSize) {
        return centerCoordinate(z, cellSize);
    }

    private static int centerCoordinate(int cellCoordinate, int cellSize) {
        validateCellSize(cellSize);
        long center = (long) cellCoordinate * cellSize + Math.floorDiv(cellSize, 2);
        if (center < Integer.MIN_VALUE || center > Integer.MAX_VALUE) {
            throw new ArithmeticException("Atmosphere cell center is outside the block-coordinate range");
        }
        return (int) center;
    }

    private static void validateCellSize(int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("Atmospheric cell size must be positive");
        }
    }
}
