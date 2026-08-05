package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Forms loaded-only rain ponds, saturated wetlands, and groundwater springs.
 *
 * <p>The manager samples a persisted deterministic cursor and the existing
 * four-by-four drainage lattice. Initial cells require a local sink and a real
 * sampled depression; later cells may grow contiguously from an owned neighbor.
 * No query loads a chunk, and all block mutations pass through canonical water
 * plus the exact reversible ownership ledger.</p>
 */
public final class RainwaterBodyManager {

    private static final int MAX_ATTEMPTS_PER_PLACEMENT = 24;
    private static final int RIM_SAMPLE_RADIUS = 3;

    private RainwaterBodyManager() {
    }

    /** Expands rain- or groundwater-fed surface water within a shared tick budget. */
    public static int expand(
            ServerLevel level,
            WatershedSavedData watersheds,
            long chunkKey,
            WatershedChunkState state,
            int maximumPlacements
    ) {
        if (maximumPlacements <= 0 || state == null) {
            return 0;
        }
        WatershedConditions.WaterFeature baseFeature = state.baseWaterFeature();
        if (baseFeature != WatershedConditions.WaterFeature.NONE
                && baseFeature != WatershedConditions.WaterFeature.AQUIFER) {
            return 0;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                ChunkPos.getX(chunkKey),
                ChunkPos.getZ(chunkKey)
        );
        if (chunk == null) {
            return 0;
        }

        TemporaryFloodSavedData ledger = TemporaryFloodSavedData.get(level);
        WatershedConditions conditions = state.conditions();
        int placed = 0;
        int attempts = 0;
        int maximumAttempts = maximumPlacements * MAX_ATTEMPTS_PER_PLACEMENT;
        while (placed < maximumPlacements && attempts++ < maximumAttempts) {
            int cursor = state.nextFloodCursor();
            int localX = cursor & 15;
            int localZ = cursor >>> 4;
            int worldX = chunk.getPos().getMinBlockX() + localX;
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            int surfaceY = surfaceHeight(level, worldX, worldZ);
            if (surfaceY == Integer.MIN_VALUE) {
                continue;
            }
            BlockPos target = new BlockPos(worldX, surfaceY, worldZ);
            if (!level.getFluidState(target.below()).isEmpty()
                    || !TemporaryFloodManager.safeTemporaryWaterTarget(level, chunk, target)) {
                continue;
            }

            SurfaceWaterKind adjacentKind = adjacentStandingKind(ledger, target);
            int cell = WatershedDrainageGrid.cell(worldX, worldZ);
            boolean localSink = state.drainageGrid().direction(cell)
                    == WatershedConditions.DrainageDirection.SINK;
            int depressionDepth = sampledDepressionDepth(level, target);
            SurfaceWaterKind kind = TransientSurfaceWaterModel.formationKind(
                    conditions,
                    depressionDepth,
                    localSink,
                    adjacentKind,
                    WaterSimulationConfig.watershedPondFormationThreshold(),
                    WaterSimulationConfig.watershedWetlandFormationThreshold(),
                    WaterSimulationConfig.watershedSpringThreshold()
            );
            if (kind == SurfaceWaterKind.NONE) {
                continue;
            }

            int volumeUnits = kind == SurfaceWaterKind.WETLAND
                    ? WaterVolumeChunk.UNITS_PER_BLOCK / 2
                    : WaterVolumeChunk.UNITS_PER_BLOCK;
            var localFlow = WatershedServices.localFlow(level, target);
            float velocityScale = kind == SurfaceWaterKind.SPRING ? 0.35f : 0.08f;
            if (TemporaryFloodManager.placeTrackedSurfaceWater(
                    level,
                    watersheds,
                    chunkKey,
                    state,
                    target,
                    kind,
                    volumeUnits,
                    localFlow.currentX() * velocityScale,
                    localFlow.currentZ() * velocityScale
            )) {
                placed++;
                conditions = state.conditions();
            }
        }
        return placed;
    }

    private static int sampledDepressionDepth(ServerLevel level, BlockPos target) {
        int north = surfaceHeight(level, target.getX(), target.getZ() - RIM_SAMPLE_RADIUS);
        int northEast = surfaceHeight(
                level,
                target.getX() + RIM_SAMPLE_RADIUS,
                target.getZ() - RIM_SAMPLE_RADIUS
        );
        int east = surfaceHeight(level, target.getX() + RIM_SAMPLE_RADIUS, target.getZ());
        int southEast = surfaceHeight(
                level,
                target.getX() + RIM_SAMPLE_RADIUS,
                target.getZ() + RIM_SAMPLE_RADIUS
        );
        int south = surfaceHeight(level, target.getX(), target.getZ() + RIM_SAMPLE_RADIUS);
        int southWest = surfaceHeight(
                level,
                target.getX() - RIM_SAMPLE_RADIUS,
                target.getZ() + RIM_SAMPLE_RADIUS
        );
        int west = surfaceHeight(level, target.getX() - RIM_SAMPLE_RADIUS, target.getZ());
        int northWest = surfaceHeight(
                level,
                target.getX() - RIM_SAMPLE_RADIUS,
                target.getZ() - RIM_SAMPLE_RADIUS
        );
        if (north == Integer.MIN_VALUE
                || northEast == Integer.MIN_VALUE
                || east == Integer.MIN_VALUE
                || southEast == Integer.MIN_VALUE
                || south == Integer.MIN_VALUE
                || southWest == Integer.MIN_VALUE
                || west == Integer.MIN_VALUE
                || northWest == Integer.MIN_VALUE) {
            return 0;
        }
        return TransientSurfaceWaterModel.depressionDepth(
                target.getY(),
                north, northEast, east, southEast, south, southWest, west, northWest
        );
    }

    private static int surfaceHeight(ServerLevel level, int blockX, int blockZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) {
            return Integer.MIN_VALUE;
        }
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15);
    }

    private static SurfaceWaterKind adjacentStandingKind(
            TemporaryFloodSavedData ledger,
            BlockPos position
    ) {
        SurfaceWaterKind bestMatch = SurfaceWaterKind.NONE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SurfaceWaterKind kind = ledger.standingKindAt(position.relative(direction));
            if (kind == SurfaceWaterKind.SPRING) {
                return kind;
            }
            if (kind == SurfaceWaterKind.RAIN_POND) {
                bestMatch = kind;
            } else if (kind == SurfaceWaterKind.WETLAND && bestMatch == SurfaceWaterKind.NONE) {
                bestMatch = kind;
            }
        }
        return bestMatch;
    }
}
