package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;

/**
 * Compact mutable server state for one chunk-scale watershed cell.
 *
 * <p>Terrain identity, hydrology, groundwater, environment, flow, and the
 * four-by-four drainage lattice are stored in seven packed longs. Save data and
 * network payloads reuse those exact words, avoiding per-field NBT compounds
 * and preventing the chunk model from growing into a per-block simulation.</p>
 */
public final class WatershedChunkState {

    /** Sentinel used when a chunk has no generated surface-water column. */
    public static final long NO_REPRESENTATIVE = Long.MIN_VALUE;

    private static final int NORMALIZED_MAX = 0xFFFF;
    private static final float MAX_PACKED_SIGNED_VALUE = 2.0f;
    private static final long DIRECTION_MASK = 0xFL;
    private static final long FEATURE_MASK = 0x7L;
    private static final long FLOODING_BIT = 1L << 23;
    private static final int DYNAMIC_FEATURE_SHIFT = 24;

    private final long basinId;
    private long terrainBits;
    private long hydrologyBits;
    private long environmentBits;
    private long flowBits;
    private long climateBits;
    private long drainageDirectionBits;
    private long drainageAccumulationBits;
    private long representativePosition;
    private long revision;
    private long lastUpdatedTick;
    private int floodCursor;
    private int activeFloodCells;
    private int activeSurfaceWaterCells;

    private WatershedChunkState(
            long basinId,
            long terrainBits,
            long hydrologyBits,
            long environmentBits,
            long flowBits,
            long climateBits,
            long drainageDirectionBits,
            long drainageAccumulationBits,
            long representativePosition,
            long revision,
            long lastUpdatedTick,
            int floodCursor,
            int activeFloodCells,
            int activeSurfaceWaterCells
    ) {
        this.basinId = basinId;
        this.terrainBits = terrainBits;
        this.hydrologyBits = hydrologyBits;
        this.environmentBits = environmentBits;
        this.flowBits = flowBits;
        this.climateBits = climateBits;
        this.drainageDirectionBits = drainageDirectionBits;
        this.drainageAccumulationBits = drainageAccumulationBits;
        this.representativePosition = representativePosition;
        this.revision = Math.max(0L, revision);
        this.lastUpdatedTick = Math.max(0L, lastUpdatedTick);
        this.floodCursor = Math.floorMod(floodCursor, 256);
        this.activeFloodCells = Math.max(0, activeFloodCells);
        this.activeSurfaceWaterCells = Math.max(0, activeSurfaceWaterCells);
    }

    /** Refreshes terrain topology while preserving basin identity and accumulated hydrology. */
    void refreshTerrain(WatershedChunkState terrain) {
        long runtimeMask = FLOODING_BIT | (FEATURE_MASK << DYNAMIC_FEATURE_SHIFT);
        terrainBits = (terrain.terrainBits & ~runtimeMask) | (terrainBits & runtimeMask);
        drainageDirectionBits = terrain.drainageDirectionBits;
        drainageAccumulationBits = terrain.drainageAccumulationBits;
        representativePosition = terrain.representativePosition;
        revision++;
    }

    /** Creates a new terrain-initialized watershed cell with dry conditions. */
    public static WatershedChunkState create(
            long basinId,
            int averageTerrainElevation,
            DrainageDirection downstreamDirection,
            WaterFeature waterFeature,
            float drainageAccumulation,
            long representativePosition,
            float floodThreshold,
            long gameTime
    ) {
        return create(
                basinId,
                averageTerrainElevation,
                downstreamDirection,
                waterFeature,
                drainageAccumulation,
                representativePosition,
                floodThreshold,
                gameTime,
                WatershedDrainageGrid.uniform(downstreamDirection),
                0.12f
        );
    }

    /** Creates a new terrain-initialized cell with compact within-chunk drainage. */
    public static WatershedChunkState create(
            long basinId,
            int averageTerrainElevation,
            DrainageDirection downstreamDirection,
            WaterFeature waterFeature,
            float drainageAccumulation,
            long representativePosition,
            float floodThreshold,
            long gameTime,
            WatershedDrainageGrid drainageGrid
    ) {
        return create(
                basinId,
                averageTerrainElevation,
                downstreamDirection,
                waterFeature,
                drainageAccumulation,
                representativePosition,
                floodThreshold,
                gameTime,
                drainageGrid,
                0.12f
        );
    }

    /** Creates a terrain cell with deterministic initial aquifer storage. */
    public static WatershedChunkState create(
            long basinId,
            int averageTerrainElevation,
            DrainageDirection downstreamDirection,
            WaterFeature waterFeature,
            float drainageAccumulation,
            long representativePosition,
            float floodThreshold,
            long gameTime,
            WatershedDrainageGrid drainageGrid,
            float initialAquiferStorage
    ) {
        long terrain = Short.toUnsignedLong((short) averageTerrainElevation);
        terrain |= (long) safeDirection(downstreamDirection).ordinal() << 16;
        terrain |= (long) safeFeature(waterFeature).ordinal() << 20;
        terrain |= (long) quantizeUnit(drainageAccumulation) << 32;
        long environment = (long) quantizeUnit(1.0f) << 32;
        long flow = (long) quantizeUnit(floodThreshold) << 48;
        long climate = (long) quantizeUnit(initialAquiferStorage) << 32;
        WatershedDrainageGrid grid = drainageGrid == null
                ? WatershedDrainageGrid.uniform(downstreamDirection)
                : drainageGrid;
        return new WatershedChunkState(
                basinId,
                terrain,
                climate,
                environment,
                flow,
                0L,
                grid.directionBits(),
                grid.accumulationBits(),
                representativePosition,
                1L,
                gameTime,
                0,
                0,
                0
        );
    }

    /** Rehydrates one state from the strict packed save/network representation. */
    public static WatershedChunkState fromPacked(Packed packed) {
        if (packed == null) {
            throw new IllegalArgumentException("Packed watershed state is required");
        }
        // Decode through public accessors once so invalid enum ids cannot leak
        // from a future or malformed payload into ordinary query paths.
        long sanitizedTerrain = packed.terrainBits;
        int directionId = (int) ((sanitizedTerrain >>> 16) & DIRECTION_MASK);
        int featureId = (int) ((sanitizedTerrain >>> 20) & FEATURE_MASK);
        int dynamicFeatureId = (int) ((sanitizedTerrain >>> DYNAMIC_FEATURE_SHIFT) & FEATURE_MASK);
        sanitizedTerrain &= ~((DIRECTION_MASK << 16)
                | (FEATURE_MASK << 20)
                | (FEATURE_MASK << DYNAMIC_FEATURE_SHIFT));
        sanitizedTerrain |= (long) DrainageDirection.fromId(directionId).ordinal() << 16;
        sanitizedTerrain |= (long) featureFromId(featureId).ordinal() << 20;
        sanitizedTerrain |= (long) featureFromId(dynamicFeatureId).ordinal() << DYNAMIC_FEATURE_SHIFT;
        return new WatershedChunkState(
                packed.basinId,
                sanitizedTerrain,
                packed.hydrologyBits,
                packed.environmentBits,
                packed.flowBits,
                packed.climateBits,
                packed.drainageDirectionBits,
                packed.drainageAccumulationBits,
                packed.representativePosition,
                packed.revision,
                packed.lastUpdatedTick,
                packed.floodCursor,
                packed.activeFloodCells,
                packed.activeSurfaceWaterCells
        );
    }

    /** Returns the exact compact state used by persistence and synchronization. */
    public Packed packed() {
        return new Packed(
                basinId,
                terrainBits,
                hydrologyBits,
                environmentBits,
                flowBits,
                climateBits,
                drainageDirectionBits,
                drainageAccumulationBits,
                representativePosition,
                revision,
                lastUpdatedTick,
                floodCursor,
                activeFloodCells,
                activeSurfaceWaterCells
        );
    }

    /** Returns the immutable public hydrologic view. */
    public WatershedConditions conditions() {
        return conditions(basinId);
    }

    /** Returns the immutable public hydrologic view under a canonical basin id. */
    public WatershedConditions conditions(long canonicalBasinId) {
        return new WatershedConditions(
                canonicalBasinId,
                (short) (terrainBits & 0xFFFFL),
                DrainageDirection.fromId((int) ((terrainBits >>> 16) & DIRECTION_MASK)),
                dequantizeUnit((int) ((terrainBits >>> 32) & NORMALIZED_MAX)),
                dequantizeUnit(word(hydrologyBits, 0)),
                dequantizeUnit(word(hydrologyBits, 1)),
                dequantizeUnit(word(climateBits, 0)),
                dequantizeUnit(word(climateBits, 1)),
                dequantizeUnit(word(climateBits, 2)),
                dequantizeUnit(word(climateBits, 3)),
                dequantizeUnit(word(hydrologyBits, 2)),
                dequantizeUnit(word(hydrologyBits, 3)),
                dequantizeSigned(word(flowBits, 0)),
                dequantizeUnit(word(environmentBits, 0)),
                dequantizeUnit(word(flowBits, 3)),
                (terrainBits & FLOODING_BIT) != 0L,
                activeFloodCells,
                activeSurfaceWaterCells,
                dequantizeUnit(word(environmentBits, 1)),
                dequantizeUnit(word(environmentBits, 2)),
                dequantizeSigned(word(flowBits, 1)),
                dequantizeSigned(word(flowBits, 2)),
                dequantizeUnit(word(environmentBits, 3)),
                effectiveWaterFeature()
        );
    }

    /** Commits one pure simulation result and returns whether synchronized fields changed. */
    public boolean apply(
            WatershedSimulationModel.Result result,
            float floodThreshold,
            long gameTime
    ) {
        return apply(result, floodThreshold, gameTime, 0.0f);
    }

    /** Blends conserved suspended material into existing presentation metadata, never creating material. */
    public boolean apply(WatershedSimulationModel.Result result, float floodThreshold,
                         long gameTime, float sedimentFloor) {
        WatershedSimulationModel.Result safe = result == null
                ? new WatershedSimulationModel.Result(
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                false, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
                : result;
        long nextHydrology = packFourUnits(
                safe.soilSaturation(),
                safe.recentRainfall(),
                safe.storedRunoff(),
                safe.riverDischarge()
        );
        float suspendedMaterial = Math.min(1.0f, Math.max(0.0f, finiteOrZero(sedimentFloor)));
        float sediment = Math.max(safe.sediment(), suspendedMaterial);
        long nextEnvironment = packFourUnits(
                safe.floodRisk(),
                sediment,
                Math.min(safe.clarity(), 1.0f - suspendedMaterial * 0.88f),
                safe.debris()
        );
        long nextFlow = packFlow(
                safe.waterLevelOffset(),
                safe.currentX(),
                safe.currentZ(),
                floodThreshold
        );
        long nextClimate = replaceWord(climateBits, 0, quantizeUnit(safe.recentSnowmelt()));
        nextClimate = replaceWord(nextClimate, 1, quantizeUnit(safe.groundwaterRecharge()));
        nextClimate = replaceWord(nextClimate, 2, quantizeUnit(safe.aquiferStorage()));
        nextClimate = replaceWord(nextClimate, 3, quantizeUnit(safe.groundwaterDischarge()));
        long nextTerrain = safe.flooding()
                ? terrainBits | FLOODING_BIT
                : terrainBits & ~FLOODING_BIT;
        boolean changed = nextHydrology != hydrologyBits
                || nextEnvironment != environmentBits
                || nextFlow != flowBits
                || nextClimate != climateBits
                || nextTerrain != terrainBits;
        hydrologyBits = nextHydrology;
        environmentBits = nextEnvironment;
        flowBits = nextFlow;
        climateBits = nextClimate;
        terrainBits = nextTerrain;
        lastUpdatedTick = Math.max(0L, gameTime);
        if (changed) {
            revision++;
        }
        return changed;
    }

    /** Adds routed upstream runoff without changing any world blocks. */
    public boolean addIncomingRunoff(float incomingRunoff) {
        int oldRunoff = word(hydrologyBits, 2);
        float combined = dequantizeUnit(oldRunoff) + Math.max(0.0f, finiteOrZero(incomingRunoff));
        int nextRunoff = quantizeUnit(combined);
        if (nextRunoff == oldRunoff) {
            return false;
        }
        hydrologyBits = replaceWord(hydrologyBits, 2, nextRunoff);
        revision++;
        return true;
    }

    /** Updates the number of exact temporary-flood cells owned by this chunk. */
    public boolean setActiveFloodCells(int count) {
        int bounded = Math.max(0, count);
        if (activeFloodCells == bounded) {
            return false;
        }
        activeFloodCells = bounded;
        revision++;
        return true;
    }

    /** Updates the number of exact pond, wetland, and spring cells in this chunk. */
    public boolean setActiveSurfaceWaterCells(int count) {
        int bounded = Math.max(0, count);
        if (activeSurfaceWaterCells == bounded) {
            return false;
        }
        activeSurfaceWaterCells = bounded;
        revision++;
        return true;
    }

    /** Sets a reversible surface feature while retaining generated terrain identity. */
    public boolean setDynamicWaterFeature(WaterFeature feature) {
        WaterFeature safe = safeFeature(feature);
        long nextTerrain = terrainBits & ~(FEATURE_MASK << DYNAMIC_FEATURE_SHIFT);
        nextTerrain |= (long) safe.ordinal() << DYNAMIC_FEATURE_SHIFT;
        if (nextTerrain == terrainBits) {
            return false;
        }
        terrainBits = nextTerrain;
        revision++;
        return true;
    }

    /** Returns the immutable generated feature beneath reversible surface water. */
    public WaterFeature baseWaterFeature() {
        return featureFromId((int) ((terrainBits >>> 20) & FEATURE_MASK));
    }

    /** Returns the active reversible pond, wetland, or spring classification. */
    public WaterFeature dynamicWaterFeature() {
        return featureFromId((int) ((terrainBits >>> DYNAMIC_FEATURE_SHIFT) & FEATURE_MASK));
    }

    /** Returns and advances the persisted deterministic 16 by 16 flood probe cursor. */
    public int nextFloodCursor() {
        int current = floodCursor;
        floodCursor = (floodCursor + 1) & 255;
        return current;
    }

    /** Returns the generated representative water position, if one exists. */
    public long representativePosition() {
        return representativePosition;
    }

    /** Replaces the representative after a safe local reclassification. */
    public boolean setRepresentativePosition(long packedPosition) {
        if (representativePosition == packedPosition) {
            return false;
        }
        representativePosition = packedPosition;
        revision++;
        return true;
    }

    /** Returns the monotonically increasing condition revision. */
    public long revision() {
        return revision;
    }

    /** Returns the last server game tick on which the hydrology model ran. */
    public long lastUpdatedTick() {
        return lastUpdatedTick;
    }

    /** Returns the compact within-chunk drainage grid. */
    public WatershedDrainageGrid drainageGrid() {
        return new WatershedDrainageGrid(drainageDirectionBits, drainageAccumulationBits);
    }

    /** Returns the stable local basin id before cross-region alias resolution. */
    public long localBasinId() {
        return basinId;
    }

    private static long packFourUnits(float first, float second, float third, float fourth) {
        return (quantizeUnit(first) & 0xFFFFL)
                | (long) quantizeUnit(second) << 16
                | (long) quantizeUnit(third) << 32
                | (long) quantizeUnit(fourth) << 48;
    }

    private static long packFlow(float offset, float currentX, float currentZ, float floodThreshold) {
        return (quantizeSigned(offset) & 0xFFFFL)
                | (long) quantizeSigned(currentX) << 16
                | (long) quantizeSigned(currentZ) << 32
                | (long) quantizeUnit(floodThreshold) << 48;
    }

    private static int word(long packed, int index) {
        return (int) ((packed >>> (index * 16)) & 0xFFFFL);
    }

    private static long replaceWord(long packed, int index, int value) {
        int shift = index * 16;
        long mask = 0xFFFFL << shift;
        return packed & ~mask | (long) (value & 0xFFFF) << shift;
    }

    private static int quantizeUnit(float value) {
        return Math.round(unit(value) * NORMALIZED_MAX);
    }

    private static float dequantizeUnit(int value) {
        return (value & NORMALIZED_MAX) / (float) NORMALIZED_MAX;
    }

    private static int quantizeSigned(float value) {
        float normalized = clamp(finiteOrZero(value) / MAX_PACKED_SIGNED_VALUE, -1.0f, 1.0f);
        return (short) Math.round(normalized * Short.MAX_VALUE) & 0xFFFF;
    }

    private static float dequantizeSigned(int value) {
        return (short) value / (float) Short.MAX_VALUE * MAX_PACKED_SIGNED_VALUE;
    }

    private static DrainageDirection safeDirection(DrainageDirection direction) {
        return direction == null ? DrainageDirection.SINK : direction;
    }

    private static WaterFeature safeFeature(WaterFeature feature) {
        return feature == null ? WaterFeature.NONE : feature;
    }

    private static WaterFeature featureFromId(int id) {
        WaterFeature[] values = WaterFeature.values();
        return id >= 0 && id < values.length ? values[id] : WaterFeature.NONE;
    }

    private WaterFeature effectiveWaterFeature() {
        WaterFeature dynamic = dynamicWaterFeature();
        return dynamic == WaterFeature.NONE ? baseWaterFeature() : dynamic;
    }

    private static float unit(float value) {
        return clamp(finiteOrZero(value), 0.0f, 1.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, finiteOrZero(value)));
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    /** Exact version-independent compact state used by saves and payloads. */
    public record Packed(
            long basinId,
            long terrainBits,
            long hydrologyBits,
            long environmentBits,
            long flowBits,
            long climateBits,
            long drainageDirectionBits,
            long drainageAccumulationBits,
            long representativePosition,
            long revision,
            long lastUpdatedTick,
            int floodCursor,
            int activeFloodCells,
            int activeSurfaceWaterCells
    ) {
        public Packed {
            revision = Math.max(0L, revision);
            lastUpdatedTick = Math.max(0L, lastUpdatedTick);
            floodCursor = Math.floorMod(floodCursor, 256);
            activeFloodCells = Math.max(0, activeFloodCells);
            activeSurfaceWaterCells = Math.max(0, activeSurfaceWaterCells);
        }

        /** Returns the same compact state under a reconciled canonical basin id. */
        public Packed withBasinId(long canonicalBasinId) {
            return new Packed(
                    canonicalBasinId,
                    terrainBits,
                    hydrologyBits,
                    environmentBits,
                    flowBits,
                    climateBits,
                    drainageDirectionBits,
                    drainageAccumulationBits,
                    representativePosition,
                    revision,
                    lastUpdatedTick,
                    floodCursor,
                    activeFloodCells,
                    activeSurfaceWaterCells
            );
        }
    }
}
