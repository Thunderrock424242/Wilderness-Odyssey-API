package com.thunder.wildernessodysseyapi.performance.background;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

/**
 * Classifies Wilderness Odyssey work using caller-selected proximity thresholds.
 *
 * <p>The helper iterates the level's already-maintained player list and does not
 * retain world state, build a cache, load chunks, or scan other dimensions.</p>
 */
public final class ActivityManager {
    private volatile boolean enabled = true;

    /** Enables or disables distance-based reduction. Disabled classification is always active. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Classifies a block position using players already present in the level. */
    public ActivityLevel classify(
            ServerLevel level,
            BlockPos position,
            DistanceThresholds thresholds,
            boolean directlyInteracting
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(thresholds, "thresholds");
        if (!enabled || directlyInteracting) {
            return ActivityLevel.ACTIVE;
        }

        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            double distanceSquared = player.distanceToSqr(
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D
            );
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
            }
        }
        return classify(!level.players().isEmpty(), nearestDistanceSquared, thresholds, false);
    }

    /** Classifies the horizontal center of a region without loading its chunk. */
    public ActivityLevel classifyChunk(
            ServerLevel level,
            ChunkPos chunk,
            int sampleY,
            DistanceThresholds thresholds,
            boolean directlyInteracting
    ) {
        Objects.requireNonNull(chunk, "chunk");
        return classify(level, new BlockPos(chunk.getMiddleBlockX(), sampleY, chunk.getMiddleBlockZ()),
                thresholds, directlyInteracting);
    }

    /**
     * Pure classification entry point used by systems that already know their
     * nearest-player distance and by unit tests.
     */
    public ActivityLevel classify(
            boolean dimensionHasPlayers,
            double nearestDistanceSquared,
            DistanceThresholds thresholds,
            boolean directlyInteracting
    ) {
        Objects.requireNonNull(thresholds, "thresholds");
        if (!enabled || directlyInteracting) {
            return ActivityLevel.ACTIVE;
        }
        if (!dimensionHasPlayers || !Double.isFinite(nearestDistanceSquared)) {
            return ActivityLevel.DORMANT;
        }
        if (nearestDistanceSquared <= thresholds.activeDistanceSquared()) {
            return ActivityLevel.ACTIVE;
        }
        if (nearestDistanceSquared <= thresholds.nearbyDistanceSquared()) {
            return ActivityLevel.NEARBY;
        }
        if (nearestDistanceSquared <= thresholds.backgroundDistanceSquared()) {
            return ActivityLevel.BACKGROUND;
        }
        return ActivityLevel.DORMANT;
    }

    /** Caller-owned activity radii; no global distance policy is imposed. */
    public record DistanceThresholds(double activeDistance, double nearbyDistance, double backgroundDistance) {
        public DistanceThresholds {
            if (!Double.isFinite(activeDistance) || activeDistance < 0.0D
                    || !Double.isFinite(nearbyDistance) || nearbyDistance < activeDistance
                    || !Double.isFinite(backgroundDistance) || backgroundDistance < nearbyDistance) {
                throw new IllegalArgumentException("Activity distances must be finite, non-negative, and ascending");
            }
        }

        public double activeDistanceSquared() {
            return activeDistance * activeDistance;
        }

        public double nearbyDistanceSquared() {
            return nearbyDistance * nearbyDistance;
        }

        public double backgroundDistanceSquared() {
            return backgroundDistance * backgroundDistance;
        }
    }
}
