package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

import java.util.List;

/**
 * Immutable, quality-capped shoreline topology consumed by client visuals.
 *
 * <p>The segment contains only positions already observed in loaded client
 * chunks. It is a cache of presentation topology, not persistent world state,
 * and it never requests or owns chunks.</p>
 */
public record CoastalSegment(
        long id,
        CoastalWaveProfile profile,
        int centerX,
        float surfaceY,
        int centerZ,
        float landwardNormalX,
        float landwardNormalZ,
        float averageBeachSlope,
        float averageWaterDepth,
        float underwaterSlope,
        List<ShorelinePoint> shoreline
) {

    public CoastalSegment {
        profile = profile == null ? CoastalWaveProfile.TEMPERATE : profile;
        surfaceY = finiteOr(surfaceY, 63.875f);
        float normalLengthSquared = landwardNormalX * landwardNormalX
                + landwardNormalZ * landwardNormalZ;
        if (!Float.isFinite(normalLengthSquared) || normalLengthSquared < 1.0e-6f) {
            landwardNormalX = 1.0f;
            landwardNormalZ = 0.0f;
        } else {
            float inverseLength = 1.0f / (float) Math.sqrt(normalLengthSquared);
            landwardNormalX *= inverseLength;
            landwardNormalZ *= inverseLength;
        }
        averageBeachSlope = finiteClamp(averageBeachSlope, 0.0f, 2.0f, 0.0f);
        averageWaterDepth = finiteClamp(averageWaterDepth, 0.0f, 64.0f, 0.0f);
        underwaterSlope = finiteClamp(underwaterSlope, 0.0f, 4.0f, 0.0f);
        shoreline = shoreline == null ? List.of() : List.copyOf(shoreline);
    }

    /** One water-edge sample and its already-loaded landward run-up cells. */
    public record ShorelinePoint(
            int waterX,
            float waterSurfaceY,
            int waterZ,
            List<RunUpCell> runUpCells,
            List<NearshoreCell> nearshoreCells
    ) {
        public ShorelinePoint {
            waterSurfaceY = finiteOr(waterSurfaceY, 63.875f);
            runUpCells = runUpCells == null ? List.of() : List.copyOf(runUpCells);
            nearshoreCells = nearshoreCells == null ? List.of() : List.copyOf(nearshoreCells);
        }
    }

    /** One terrain-top quad available to the thin run-up and wet-sand pass. */
    public record RunUpCell(
            int blockX,
            int topBlockY,
            int blockZ,
            float distanceFromWaterBlocks
    ) {
        public RunUpCell {
            distanceFromWaterBlocks = finiteClamp(
                    distanceFromWaterBlocks, 0.0f, 32.0f, 0.0f);
        }
    }

    /** One loaded ocean cell used to locate the depth-relative breaker zone. */
    public record NearshoreCell(
            int blockX,
            float waterSurfaceY,
            int blockZ,
            float depthBlocks,
            float distanceFromShoreBlocks
    ) {
        public NearshoreCell {
            waterSurfaceY = finiteOr(waterSurfaceY, 63.875f);
            depthBlocks = finiteClamp(depthBlocks, 0.0f, 64.0f, 0.0f);
            distanceFromShoreBlocks = finiteClamp(
                    distanceFromShoreBlocks, 0.0f, 32.0f, 0.0f);
        }
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
