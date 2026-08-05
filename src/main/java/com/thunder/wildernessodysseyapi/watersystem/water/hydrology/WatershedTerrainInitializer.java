package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds deterministic watershed metadata from one already-loaded chunk.
 *
 * <p>The sampler reads a fixed local height lattice and compact generated-water
 * spans only. It never asks the chunk source for neighbors, so chunk load and
 * partial world generation cannot cascade into distant synchronous work.</p>
 */
public final class WatershedTerrainInitializer {

    private static final int BASIN_REGION_SIZE_CHUNKS = 8;
    private static final int[] LATTICE = {2, 6, 10, 14};

    private WatershedTerrainInitializer() {
    }

    /** Creates one stable chunk-scale terrain and water classification. */
    public static WatershedChunkState initialize(ServerLevel level, LevelChunk chunk) {
        int[] heights = latticeHeights(chunk);
        int averageElevation = averageElevation(heights);
        DrainageDirection direction = DrainageDirectionCalculator.calculate(
                centerHeight(chunk),
                edgeHeight(chunk, 8, 1),
                edgeHeight(chunk, 14, 1),
                edgeHeight(chunk, 14, 8),
                edgeHeight(chunk, 14, 14),
                edgeHeight(chunk, 8, 14),
                edgeHeight(chunk, 1, 14),
                edgeHeight(chunk, 1, 8),
                edgeHeight(chunk, 1, 1)
        );
        WaterClassification classification = classifyWater(chunk);
        float accumulation = drainageAccumulation(
                classification.feature,
                classification.exposedColumns,
                relief(heights)
        );
        WatershedDrainageGrid drainageGrid = WatershedDrainageGrid.fromHeights(heights, direction);
        return WatershedChunkState.create(
                localBasinId(level, chunk.getPos()),
                averageElevation,
                direction,
                classification.feature,
                accumulation,
                classification.representativePosition,
                WaterSimulationConfig.watershedFloodThreshold(),
                level.getGameTime(),
                drainageGrid,
                initialAquiferStorage(classification, relief(heights), accumulation)
        );
    }

    private static int[] latticeHeights(LevelChunk chunk) {
        int[] heights = new int[LATTICE.length * LATTICE.length];
        int index = 0;
        for (int localZ : LATTICE) {
            for (int localX : LATTICE) {
                heights[index++] = height(chunk, localX, localZ);
            }
        }
        return heights;
    }

    private static int averageElevation(int[] heights) {
        int sum = 0;
        for (int height : heights) {
            sum += height;
        }
        return Math.round(sum / (float) heights.length);
    }

    private static double centerHeight(LevelChunk chunk) {
        return (height(chunk, 6, 6)
                + height(chunk, 10, 6)
                + height(chunk, 6, 10)
                + height(chunk, 10, 10)) * 0.25;
    }

    private static double edgeHeight(LevelChunk chunk, int localX, int localZ) {
        int perpendicularX = localZ == 1 || localZ == 14 ? 1 : 0;
        int perpendicularZ = localX == 1 || localX == 14 ? 1 : 0;
        return (height(chunk, localX, localZ)
                + height(chunk, clampLocal(localX - perpendicularX * 3), clampLocal(localZ - perpendicularZ * 3))
                + height(chunk, clampLocal(localX + perpendicularX * 3), clampLocal(localZ + perpendicularZ * 3))) / 3.0;
    }

    private static int relief(int[] heights) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int height : heights) {
            minimum = Math.min(minimum, height);
            maximum = Math.max(maximum, height);
        }
        return Math.max(0, maximum - minimum);
    }

    private static int height(LevelChunk chunk, int localX, int localZ) {
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, clampLocal(localX), clampLocal(localZ));
    }

    private static int clampLocal(int coordinate) {
        return Math.max(0, Math.min(15, coordinate));
    }

    private static WaterClassification classifyWater(LevelChunk chunk) {
        GeneratedWaterChunk generated = chunk.getExistingData(ModAttachments.GENERATED_WATER).orElse(null);
        if (generated == null || generated.spanCount() == 0) {
            return WaterClassification.DRY;
        }
        int[] counts = new int[WaterFeature.values().length];
        long representative = WatershedChunkState.NO_REPRESENTATIVE;
        int exposed = 0;
        int aquiferColumns = 0;
        GeneratedWaterChunk.Snapshot snapshot = generated.snapshot();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                GeneratedWaterChunk.WaterSpan top = snapshot.topSpan(localX, localZ);
                if (top != null && top.cell().bodyType() == GeneratedWaterChunk.BodyType.AQUIFER) {
                    aquiferColumns++;
                }
                if (top == null || snapshot.surfaceCovered(localX, localZ)) {
                    continue;
                }
                WaterFeature feature = feature(top.cell().bodyType());
                counts[feature.ordinal()]++;
                exposed++;
                if (representative == WatershedChunkState.NO_REPRESENTATIVE) {
                    representative = new BlockPos(
                            chunk.getPos().getMinBlockX() + localX,
                            top.topY(),
                            chunk.getPos().getMinBlockZ() + localZ
                    ).asLong();
                }
            }
        }
        WaterFeature dominant = WaterFeature.NONE;
        int dominantCount = 0;
        for (WaterFeature feature : WaterFeature.values()) {
            if (counts[feature.ordinal()] > dominantCount) {
                dominant = feature;
                dominantCount = counts[feature.ordinal()];
            }
        }
        return dominantCount == 0
                ? new WaterClassification(
                WaterFeature.NONE,
                0,
                WatershedChunkState.NO_REPRESENTATIVE,
                aquiferColumns
        )
                : new WaterClassification(dominant, exposed, representative, aquiferColumns);
    }

    private static WaterFeature feature(GeneratedWaterChunk.BodyType bodyType) {
        return switch (bodyType) {
            case OCEAN -> WaterFeature.COASTAL;
            case RIVER -> WaterFeature.RIVER;
            case LAKE -> WaterFeature.LAKE;
            case AQUIFER -> WaterFeature.AQUIFER;
            case SPRING -> WaterFeature.STREAM;
        };
    }

    private static float drainageAccumulation(WaterFeature feature, int exposedColumns, int relief) {
        float coverage = Math.min(1.0f, exposedColumns / 256.0f);
        float reliefContribution = Math.min(1.0f, relief / 48.0f);
        float base = switch (feature) {
            case STREAM -> 0.32f;
            case RIVER -> 0.62f;
            case LAKE -> 0.52f;
            case WETLAND -> 0.44f;
            case COASTAL -> 0.78f;
            case AQUIFER -> 0.18f;
            case POND -> 0.38f;
            case NONE -> 0.08f;
        };
        return Math.min(1.0f, base + coverage * 0.24f + reliefContribution * 0.10f);
    }

    private static float initialAquiferStorage(
            WaterClassification classification,
            int relief,
            float accumulation
    ) {
        float generatedAquiferCoverage = classification.aquiferColumns / 256.0f;
        float lowReliefRetention = 1.0f - Math.min(1.0f, relief / 48.0f);
        float surfaceWaterRecharge = classification.feature == WaterFeature.LAKE
                || classification.feature == WaterFeature.WETLAND
                ? 0.08f
                : 0.0f;
        return Math.min(0.72f,
                0.08f
                        + generatedAquiferCoverage * 0.52f
                        + lowReliefRetention * 0.08f
                        + accumulation * 0.05f
                        + surfaceWaterRecharge);
    }

    private static long localBasinId(ServerLevel level, ChunkPos chunkPos) {
        long regionX = Math.floorDiv(chunkPos.x, BASIN_REGION_SIZE_CHUNKS);
        long regionZ = Math.floorDiv(chunkPos.z, BASIN_REGION_SIZE_CHUNKS);
        long value = level.getSeed()
                ^ regionX * 0x9E3779B97F4A7C15L
                ^ regionZ * 0xC2B2AE3D27D4EB4FL
                ^ level.dimension().location().toString().hashCode() * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value == 0L ? 1L : value;
    }

    private record WaterClassification(
            WaterFeature feature,
            int exposedColumns,
            long representativePosition,
            int aquiferColumns
    ) {
        private static final WaterClassification DRY = new WaterClassification(
                WaterFeature.NONE,
                0,
                WatershedChunkState.NO_REPRESENTATIVE,
                0
        );
    }
}
