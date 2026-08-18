package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import net.minecraft.core.BlockPos;

/**
 * Horizontal identity of a coarse ecosystem simulation cell.
 *
 * <p>Cells are intentionally wider than chunks. Every entity in a cell shares
 * one cached nearest-player level instead of repeating player searches.</p>
 */
public record EcosystemCellKey(int x, int z) {

    /** Resolves a block position with floor division so negative coordinates remain stable. */
    public static EcosystemCellKey fromBlock(BlockPos position, int cellSize) {
        int safeSize = Math.max(1, cellSize);
        return new EcosystemCellKey(
                Math.floorDiv(position.getX(), safeSize),
                Math.floorDiv(position.getZ(), safeSize)
        );
    }

    /** Restores a key packed by {@link #packed()}. */
    public static EcosystemCellKey fromPacked(long packed) {
        return new EcosystemCellKey((int) packed, (int) (packed >>> 32));
    }

    /** Packs signed cell coordinates into a map-friendly long. */
    public long packed() {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    /** Returns the minimum block X included in this cell. */
    public int minimumBlockX(int cellSize) {
        return Math.multiplyExact(x, cellSize);
    }

    /** Returns the minimum block Z included in this cell. */
    public int minimumBlockZ(int cellSize) {
        return Math.multiplyExact(z, cellSize);
    }

    /** Returns the horizontal cell center at a supplied representative height. */
    public BlockPos center(int cellSize, int y) {
        int half = cellSize / 2;
        return new BlockPos(minimumBlockX(cellSize) + half, y, minimumBlockZ(cellSize) + half);
    }
}
