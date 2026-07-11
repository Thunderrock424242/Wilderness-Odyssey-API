package com.thunder.wildernessodysseyapi.watersystem.water.api;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;

/**
 * Defines the fixed-point volume unit used by the public water API.
 *
 * <p>Compatibility code should translate buckets, fluid handlers, and machines
 * at its boundary, then pass these units to the water authority. This prevents
 * external container sizes from becoming simulation constants.</p>
 */
public final class WaterUnits {

    /** Number of authority units represented by one full block of water. */
    public static final long UNITS_PER_BLOCK = WaterVolumeChunk.UNITS_PER_BLOCK;

    /** Current vanilla bucket-equivalent amount, kept at the adapter boundary. */
    public static final long VANILLA_BUCKET_EQUIVALENT = UNITS_PER_BLOCK;

    private WaterUnits() {
    }
}
