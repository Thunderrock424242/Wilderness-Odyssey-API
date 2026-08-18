package com.thunder.wildernessodysseyapi.ecosystem.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * Immutable, lazily decayed view of one chunk-sized environmental-memory cell.
 *
 * <p>Values are normalized to {@code 0.0-1.0}. The snapshot records both the
 * latest activity and how much general disturbance decayed since that update,
 * allowing ecosystem systems and diagnostics to share the same read model.</p>
 */
public record EnvironmentalMemory(
        ChunkPos cell,
        double disturbance,
        double recentFireActivity,
        double recentCombatActivity,
        double playerTraffic,
        long lastUpdatedGameTime,
        long observedGameTime,
        double disturbanceDecayApplied,
        DisturbanceSource lastSource,
        BlockPos lastSourcePosition,
        UUID lastSourceId
) {
    public EnvironmentalMemory {
        cell = new ChunkPos(cell.x, cell.z);
        disturbance = unit(disturbance);
        recentFireActivity = unit(recentFireActivity);
        recentCombatActivity = unit(recentCombatActivity);
        playerTraffic = unit(playerTraffic);
        disturbanceDecayApplied = Math.max(0.0, finite(disturbanceDecayApplied));
        lastSource = lastSource == null ? DisturbanceSource.OTHER : lastSource;
        lastSourcePosition = lastSourcePosition == null
                ? new BlockPos(cell.getMiddleBlockX(), 0, cell.getMiddleBlockZ())
                : lastSourcePosition.immutable();
    }

    /** Returns the non-negative elapsed time used for this lazy-decay view. */
    public long elapsedTicks() {
        return Math.max(0L, observedGameTime - lastUpdatedGameTime);
    }

    /** Returns the strongest surviving normalized channel in this cell. */
    public double strongestActivity() {
        return Math.max(Math.max(disturbance, playerTraffic),
                Math.max(recentFireActivity, recentCombatActivity));
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, finite(value)));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
