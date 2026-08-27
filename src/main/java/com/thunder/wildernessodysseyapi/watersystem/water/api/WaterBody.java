package com.thunder.wildernessodysseyapi.watersystem.water.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Read-only water-body metadata returned by {@link WaterAccess}.
 *
 * <p>The current hybrid model identifies cached large bodies at chunk-column
 * granularity. The key is therefore suitable for diagnostics and short-lived
 * references, but must not be persisted by callers as a permanent body UUID.</p>
 *
 * @param dimension dimension containing the sampled body
 * @param regionKey authority-owned region key
 * @param kind current storage/body representation
 * @param surfaceHeight animated surface height at the query column
 * @param depth approximate water depth at the query column
 * @param estimatedVolumeUnits estimated fixed-point body volume
 * @param current current velocity at the query column
 */
public record WaterBody(
        ResourceKey<Level> dimension,
        long regionKey,
        Kind kind,
        double surfaceHeight,
        double depth,
        long estimatedVolumeUnits,
        Vec3 current
) {

    /** Current high-level representations exposed without leaking storage classes. */
    public enum Kind {
        LOCAL_VOLUME,
        LARGE_OCEAN,
        LARGE_RIVER,
        LARGE_POND,
        LARGE_COAST,
        LARGE_LAKE
    }
}
