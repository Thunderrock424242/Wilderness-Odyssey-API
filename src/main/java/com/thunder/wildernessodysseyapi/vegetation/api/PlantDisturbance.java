package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Immutable, bounded request describing environmental pressure on vegetation.
 *
 * <p>Publishing a request does not itself authorize block mutation. The
 * vegetation owner checks {@link #allowBlockDamage()} and the selected plant's
 * type before applying any physical result.</p>
 */
public record PlantDisturbance(
        PlantDisturbanceType type,
        BlockPos center,
        int radiusBlocks,
        double intensity,
        long createdAt,
        long expiresAt,
        boolean allowBlockDamage
) {

    /** Validates a request before it enters the bounded regional ledger. */
    public PlantDisturbance {
        type = Objects.requireNonNullElse(type, PlantDisturbanceType.WIND);
        center = Objects.requireNonNullElse(center, BlockPos.ZERO).immutable();
        radiusBlocks = Math.max(0, Math.min(256, radiusBlocks));
        intensity = unit(intensity);
        createdAt = Math.max(0L, createdAt);
        expiresAt = Math.max(createdAt, expiresAt);
    }

    /** Creates a duration-based request at the current server game time. */
    public static PlantDisturbance lasting(
            PlantDisturbanceType type,
            BlockPos center,
            int radiusBlocks,
            double intensity,
            long gameTime,
            int durationTicks,
            boolean allowBlockDamage
    ) {
        long safeTime = Math.max(0L, gameTime);
        long duration = Math.max(1L, durationTicks);
        long expiry = safeTime > Long.MAX_VALUE - duration ? Long.MAX_VALUE : safeTime + duration;
        return new PlantDisturbance(
                type, center, radiusBlocks, intensity, safeTime, expiry, allowBlockDamage
        );
    }

    /** Returns linearly decayed local intensity, including distance falloff. */
    public double intensityAt(BlockPos position, long gameTime) {
        if (position == null || gameTime >= expiresAt) {
            return 0.0;
        }
        long dx = (long) position.getX() - center.getX();
        long dz = (long) position.getZ() - center.getZ();
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (distance > radiusBlocks) {
            return 0.0;
        }
        double spatial = radiusBlocks == 0 ? 1.0 : 1.0 - distance / Math.max(1.0, radiusBlocks);
        long lifetime = Math.max(1L, expiresAt - createdAt);
        double temporal = 1.0 - (double) Math.max(0L, gameTime - createdAt) / lifetime;
        return unit(intensity * spatial * temporal);
    }

    private static double unit(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(0.0, Math.min(1.0, finite));
    }
}
