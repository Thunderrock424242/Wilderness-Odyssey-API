package com.thunder.wildernessodysseyapi.meteor.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Immutable seeded parameters shared by every chunk of one generated crater. */
public record MeteorCraterPlan(BlockPos center, int radius, int depth, int rimHeight,
                               int ejectaRange, long seed) {

    // Vanilla structure references search eight neighboring chunks. Centered
    // at a chunk midpoint, this footprint reaches no farther than that ring.
    public static final int MAXIMUM_FOOTPRINT_RADIUS = 127;

    public MeteorCraterPlan {
        center = center.immutable();
        radius = Math.max(60, Math.min(125, radius));
        depth = Math.max(12, Math.min(40, depth));
        rimHeight = Math.max(8, Math.min(20, rimHeight));
        ejectaRange = Math.max(1, Math.min(69, ejectaRange));
    }

    /** Uses only the generation context's seeded random source. */
    public static MeteorCraterPlan create(BlockPos center, RandomSource random) {
        int radius = 60 + random.nextInt(66);
        return new MeteorCraterPlan(center, radius, radius / 5 + random.nextInt(8),
                8 + random.nextInt(13), (int) (radius * 0.4) + random.nextInt(20), random.nextLong());
    }

    public int footprintRadius() {
        return Math.min(MAXIMUM_FOOTPRINT_RADIUS,
                radius + Math.max(ejectaRange, rimHeight + 8));
    }

    /** Covers vertical carving while retaining chunk-local placement bounds. */
    public BoundingBox bounds(int minimumY, int maximumY) {
        int extent = footprintRadius();
        return new BoundingBox(center.getX() - extent, minimumY, center.getZ() - extent,
                center.getX() + extent, maximumY, center.getZ() + extent);
    }
}
