package com.thunder.wildernessodysseyapi.simulation.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/**
 * Lightweight orchestration key aligned to an existing caller-selected cell size.
 *
 * <p>This is not a persisted world partition. The initial engine uses the
 * ecosystem's established 64-by-64 cells for request deduplication while each
 * owner keeps its own specialized weather, watershed, vegetation, or LOD grid.</p>
 */
public record SimulationRegion(
        ResourceLocation dimension,
        int cellX,
        int cellZ,
        int cellSize,
        int sampleY
) {
    public SimulationRegion {
        dimension = Objects.requireNonNull(dimension, "Dimension is required");
        if (cellSize < 16 || cellSize > 256) {
            throw new IllegalArgumentException("Simulation cell size must be between 16 and 256 blocks");
        }
    }

    /** Creates a region without loading or querying the containing chunk. */
    public static SimulationRegion fromBlock(
            ResourceLocation dimension,
            BlockPos position,
            int cellSize
    ) {
        Objects.requireNonNull(position, "Position is required");
        return new SimulationRegion(
                dimension,
                Math.floorDiv(position.getX(), cellSize),
                Math.floorDiv(position.getZ(), cellSize),
                cellSize,
                position.getY()
        );
    }

    /** Returns a clamped cell-center sample point without forcing a chunk load. */
    public BlockPos anchor(ServerLevel level) {
        Objects.requireNonNull(level, "Server level is required");
        int y = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, sampleY));
        return new BlockPos(
                cellX * cellSize + cellSize / 2,
                y,
                cellZ * cellSize + cellSize / 2
        );
    }
}
