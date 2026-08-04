package com.thunder.wildernessodysseyapi.watersystem.water.api;

/**
 * Immutable chunk-scale hydrologic conditions exposed by Wilderness water.
 *
 * <p>The server owns these values and clients receive quantized snapshots. None
 * of the fields are a second water-volume authority: physical mutations still
 * pass through {@link WaterAccess}. Values described as normalized are bounded
 * to {@code [0, 1]}, currents are blocks per tick, and the level offset is in
 * blocks relative to the generated surface.</p>
 */
public record WatershedConditions(
        long basinId,
        int averageTerrainElevation,
        DrainageDirection downstreamDirection,
        float drainageAccumulation,
        float soilSaturation,
        float recentRainfall,
        float recentSnowmelt,
        float storedRunoff,
        float riverDischarge,
        float waterLevelOffset,
        float floodRisk,
        float floodThreshold,
        boolean flooding,
        int activeTemporaryFloodCells,
        float sediment,
        float clarity,
        float currentX,
        float currentZ,
        float debris,
        WaterFeature waterFeature
) {

    /** Shared result for an uninitialized, disabled, or unloaded chunk. */
    public static final WatershedConditions NONE = new WatershedConditions(
            0L,
            0,
            DrainageDirection.SINK,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            false,
            0,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            WaterFeature.NONE
    );

    public WatershedConditions {
        downstreamDirection = downstreamDirection == null
                ? DrainageDirection.SINK
                : downstreamDirection;
        drainageAccumulation = unit(drainageAccumulation);
        soilSaturation = unit(soilSaturation);
        recentRainfall = unit(recentRainfall);
        recentSnowmelt = unit(recentSnowmelt);
        storedRunoff = unit(storedRunoff);
        riverDischarge = unit(riverDischarge);
        waterLevelOffset = finiteOrZero(waterLevelOffset);
        floodRisk = unit(floodRisk);
        floodThreshold = unit(floodThreshold);
        activeTemporaryFloodCells = Math.max(0, activeTemporaryFloodCells);
        sediment = unit(sediment);
        clarity = unit(clarity);
        currentX = finiteOrZero(currentX);
        currentZ = finiteOrZero(currentZ);
        debris = unit(debris);
        waterFeature = waterFeature == null ? WaterFeature.NONE : waterFeature;
    }

    /** Returns current speed without allocating a Minecraft vector. */
    public float currentStrength() {
        return (float) Math.hypot(currentX, currentZ);
    }

    /** Returns the same conditions under a reconciled canonical basin id. */
    public WatershedConditions withBasinId(long canonicalBasinId) {
        if (basinId == canonicalBasinId) {
            return this;
        }
        return new WatershedConditions(
                canonicalBasinId,
                averageTerrainElevation,
                downstreamDirection,
                drainageAccumulation,
                soilSaturation,
                recentRainfall,
                recentSnowmelt,
                storedRunoff,
                riverDischarge,
                waterLevelOffset,
                floodRisk,
                floodThreshold,
                flooding,
                activeTemporaryFloodCells,
                sediment,
                clarity,
                currentX,
                currentZ,
                debris,
                waterFeature
        );
    }

    /** Returns whether this chunk has generated surface water worth simulating. */
    public boolean hasSurfaceWater() {
        return waterFeature != WaterFeature.NONE && waterFeature != WaterFeature.AQUIFER;
    }

    private static float unit(float value) {
        return Math.max(0.0f, Math.min(1.0f, finiteOrZero(value)));
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    /** Eight-way chunk drainage direction plus a local sink. */
    public enum DrainageDirection {
        SINK(0, 0),
        NORTH(0, -1),
        NORTH_EAST(1, -1),
        EAST(1, 0),
        SOUTH_EAST(1, 1),
        SOUTH(0, 1),
        SOUTH_WEST(-1, 1),
        WEST(-1, 0),
        NORTH_WEST(-1, -1);

        private final int stepX;
        private final int stepZ;

        DrainageDirection(int stepX, int stepZ) {
            this.stepX = stepX;
            this.stepZ = stepZ;
        }

        /** Returns the downstream chunk-X step. */
        public int stepX() {
            return stepX;
        }

        /** Returns the downstream chunk-Z step. */
        public int stepZ() {
            return stepZ;
        }

        /** Returns the normalized east-west current component. */
        public float unitX() {
            return stepX == 0 || stepZ == 0 ? stepX : stepX * 0.70710677f;
        }

        /** Returns the normalized north-south current component. */
        public float unitZ() {
            return stepX == 0 || stepZ == 0 ? stepZ : stepZ * 0.70710677f;
        }

        /** Decodes a persisted ordinal with a safe sink fallback. */
        public static DrainageDirection fromId(int id) {
            DrainageDirection[] values = values();
            return id >= 0 && id < values.length ? values[id] : SINK;
        }
    }

    /** Coarse generated-water classification used by hydrologic balancing. */
    public enum WaterFeature {
        NONE,
        STREAM,
        RIVER,
        LAKE,
        WETLAND,
        COASTAL,
        AQUIFER
    }
}
