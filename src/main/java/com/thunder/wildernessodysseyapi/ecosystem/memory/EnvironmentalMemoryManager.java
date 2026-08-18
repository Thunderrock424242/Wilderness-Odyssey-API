package com.thunder.wildernessodysseyapi.ecosystem.memory;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public server-authoritative API for regional environmental memory.
 *
 * <p>Each dimension owns a separate chunk-keyed {@link net.minecraft.world.level.saveddata.SavedData}
 * ledger. Reads are constant-time for one cell, decay is lazy, and maintenance
 * checks only one rotating stored cell per API access.</p>
 */
public final class EnvironmentalMemoryManager {

    private EnvironmentalMemoryManager() {
    }

    /** Returns the normalized, lazily decayed disturbance at a block position. */
    public static double getDisturbance(ServerLevel level, BlockPos position) {
        return getMemory(level, position).map(EnvironmentalMemory::disturbance).orElse(0.0);
    }

    /** Returns the current chunk-sized memory cell, or empty after it decays away. */
    public static Optional<EnvironmentalMemory> getMemory(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "Server level is required");
        Objects.requireNonNull(position, "Memory position is required");
        EnvironmentalMemorySavedData data = EnvironmentalMemorySavedData.get(level);
        EnvironmentalMemorySavedData.Settings settings = settings();
        data.pruneOne(level.getGameTime(), settings);
        return data.memory(new ChunkPos(position).toLong(), level.getGameTime(), settings);
    }

    /**
     * Adds activity to one regional cell without attributing it to a specific entity.
     *
     * @return the updated normalized memory snapshot
     */
    public static EnvironmentalMemory addDisturbance(
            ServerLevel level,
            BlockPos position,
            double amount,
            DisturbanceSource source
    ) {
        return addDisturbance(level, position, amount, source, null);
    }

    /**
     * Adds activity to one regional cell and retains the latest contributing entity ID.
     *
     * <p>Amounts combine additively and are capped by the server configuration.
     * Negative or non-finite values are rejected before any world data changes.</p>
     *
     * @return the updated normalized memory snapshot
     */
    public static EnvironmentalMemory addDisturbance(
            ServerLevel level,
            BlockPos position,
            double amount,
            DisturbanceSource source,
            UUID sourceId
    ) {
        Objects.requireNonNull(level, "Server level is required");
        Objects.requireNonNull(position, "Memory position is required");
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Disturbance amount must be finite and greater than zero");
        }
        DisturbanceSource safeSource = source == null ? DisturbanceSource.OTHER : source;
        EnvironmentalMemorySavedData data = EnvironmentalMemorySavedData.get(level);
        EnvironmentalMemorySavedData.Settings settings = settings();
        data.pruneOne(level.getGameTime(), settings);
        return data.add(
                new ChunkPos(position).toLong(),
                position.immutable(),
                amount,
                safeSource,
                sourceId,
                level.getGameTime(),
                settings
        );
    }

    /** Clears exactly one chunk-sized environmental-memory cell. */
    public static boolean clearRegion(ServerLevel level, ChunkPos cell) {
        Objects.requireNonNull(level, "Server level is required");
        Objects.requireNonNull(cell, "Memory cell is required");
        return EnvironmentalMemorySavedData.get(level).clear(cell.toLong());
    }

    /**
     * Clears a bounded square of chunk cells around a block position.
     *
     * @return how many stored cells were removed
     */
    public static int clearRegion(ServerLevel level, BlockPos center, int chunkRadius) {
        Objects.requireNonNull(level, "Server level is required");
        Objects.requireNonNull(center, "Region center is required");
        if (chunkRadius < 0 || chunkRadius > 32) {
            throw new IllegalArgumentException("Chunk radius must be between 0 and 32");
        }
        EnvironmentalMemorySavedData data = EnvironmentalMemorySavedData.get(level);
        ChunkPos origin = new ChunkPos(center);
        int removed = 0;
        for (int offsetX = -chunkRadius; offsetX <= chunkRadius; offsetX++) {
            for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius; offsetZ++) {
                if (data.clear(ChunkPos.asLong(origin.x + offsetX, origin.z + offsetZ))) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Returns the number of currently stored cells in this dimension. */
    public static int getActiveCellCount(ServerLevel level) {
        Objects.requireNonNull(level, "Server level is required");
        EnvironmentalMemorySavedData data = EnvironmentalMemorySavedData.get(level);
        EnvironmentalMemorySavedData.Settings settings = settings();
        data.pruneOne(level.getGameTime(), settings);
        return data.size();
    }

    /**
     * Finds the strongest nearby regional disturbance using bounded chunk lookups.
     *
     * <p>The ecosystem radius is configuration-capped at 64 blocks, so the
     * worst case is only an 81-cell map lookup and never a block or entity scan.</p>
     */
    public static Optional<EnvironmentalContext.Disturbance> findStrongestDisturbance(
            ServerLevel level,
            BlockPos position,
            int radius
    ) {
        Objects.requireNonNull(level, "Server level is required");
        Objects.requireNonNull(position, "Query position is required");
        if (radius <= 0) {
            return Optional.empty();
        }
        int boundedRadius = Math.min(64, radius);
        int chunkRadius = (boundedRadius + 15) >> 4;
        double radiusSquared = (double) boundedRadius * boundedRadius;
        long gameTime = level.getGameTime();
        EnvironmentalMemorySavedData data = EnvironmentalMemorySavedData.get(level);
        EnvironmentalMemorySavedData.Settings settings = settings();
        data.pruneOne(gameTime, settings);

        ChunkPos origin = new ChunkPos(position);
        EnvironmentalMemory strongest = null;
        double strongestScore = 0.0;
        for (int offsetX = -chunkRadius; offsetX <= chunkRadius; offsetX++) {
            for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius; offsetZ++) {
                ChunkPos candidatePos = new ChunkPos(origin.x + offsetX, origin.z + offsetZ);
                if (horizontalDistanceSquared(position, candidatePos) > radiusSquared) {
                    continue;
                }
                Optional<EnvironmentalMemory> candidate = data.memory(
                        candidatePos.toLong(), gameTime, settings);
                if (candidate.isEmpty()) {
                    continue;
                }
                EnvironmentalMemory memory = candidate.get();
                double sourceDistance = Math.sqrt(memory.lastSourcePosition().distSqr(position));
                double proximity = 1.0 - 0.25 * Math.min(1.0, sourceDistance / boundedRadius);
                double score = memory.disturbance() * proximity;
                if (score > strongestScore) {
                    strongestScore = score;
                    strongest = memory;
                }
            }
        }
        if (strongest == null) {
            return Optional.empty();
        }
        return Optional.of(new EnvironmentalContext.Disturbance(
                strongest.lastSourcePosition(),
                strongest.lastSourceId(),
                strongest.disturbance(),
                gameTime
        ));
    }

    private static EnvironmentalMemorySavedData.Settings settings() {
        return new EnvironmentalMemorySavedData.Settings(
                EcosystemConfig.DISTURBANCE_DECAY_PER_DAY.get(),
                EcosystemConfig.DISTURBANCE_CLEANUP_THRESHOLD.get(),
                EcosystemConfig.MAXIMUM_DISTURBANCE.get()
        );
    }

    private static double horizontalDistanceSquared(BlockPos position, ChunkPos cell) {
        int nearestX = Math.max(cell.getMinBlockX(), Math.min(position.getX(), cell.getMaxBlockX()));
        int nearestZ = Math.max(cell.getMinBlockZ(), Math.min(position.getZ(), cell.getMaxBlockZ()));
        long deltaX = (long) position.getX() - nearestX;
        long deltaZ = (long) position.getZ() - nearestZ;
        return (double) deltaX * deltaX + (double) deltaZ * deltaZ;
    }
}
