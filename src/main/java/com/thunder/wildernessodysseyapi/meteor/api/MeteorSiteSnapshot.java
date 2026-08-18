package com.thunder.wildernessodysseyapi.meteor.api;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Immutable nearest-site result shared by radiation, story, ecology, and clients. */
public record MeteorSiteSnapshot(
        boolean present,
        UUID id,
        BlockPos center,
        int craterRadius,
        long createdAt,
        double intensity,
        MeteorSiteSource source,
        double horizontalDistance,
        double radiation
) {

    /** Shared absent result that carries no world position or hazard. */
    public static final MeteorSiteSnapshot NONE = new MeteorSiteSnapshot(
            false, new UUID(0L, 0L), BlockPos.ZERO, 0, 0L,
            0.0, MeteorSiteSource.UNKNOWN, Double.POSITIVE_INFINITY, 0.0
    );

    /** Normalizes persisted and calculated values before publication. */
    public MeteorSiteSnapshot {
        id = id == null ? new UUID(0L, 0L) : id;
        center = center == null ? BlockPos.ZERO : center.immutable();
        craterRadius = Math.max(0, Math.min(1_024, craterRadius));
        createdAt = Math.max(0L, createdAt);
        intensity = unit(intensity);
        source = source == null ? MeteorSiteSource.UNKNOWN : source;
        horizontalDistance = Double.isFinite(horizontalDistance)
                ? Math.max(0.0, horizontalDistance) : Double.POSITIVE_INFINITY;
        radiation = unit(radiation);
        if (!present) {
            intensity = 0.0;
            radiation = 0.0;
        }
    }

    /** Returns the cylindrical gameplay radiation radius for this site. */
    public double radiationRadius() {
        return craterRadius * 1.5;
    }

    private static double unit(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(0.0, Math.min(1.0, finite));
    }
}
