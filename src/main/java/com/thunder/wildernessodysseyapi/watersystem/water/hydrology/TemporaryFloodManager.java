package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Performs loaded-only, budgeted temporary flood expansion and recession.
 *
 * <p>Expansion probes a persisted deterministic 16 by 16 cursor and accepts
 * only low, adjacent, replaceable positions outside structure bounds. Recession
 * uses the independent exact-position ledger and canonical flood flag together;
 * neither metadata source can delete a block by itself.</p>
 */
public final class TemporaryFloodManager {

    private static final int MAX_ATTEMPTS_PER_PLACEMENT = 16;
    private static final int MAX_RECESSION_SCAN_MULTIPLIER = 12;

    private TemporaryFloodManager() {
    }

    /** Gradually expands one currently flooding chunk within a shared tick budget. */
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
        WatershedConditions conditions = state.conditions();
        if (!conditions.flooding()
                || !conditions.hasSurfaceWater()
                || state.representativePosition() == WatershedChunkState.NO_REPRESENTATIVE) {
            return 0;
        }
        BlockPos representative = BlockPos.of(state.representativePosition());
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                ChunkPos.getX(chunkKey),
                ChunkPos.getZ(chunkKey)
        );
        if (chunk == null) {
            return 0;
        }

        WaterAccess water = WaterServices.access();
        TemporaryFloodSavedData ledger = TemporaryFloodSavedData.get(level);
        int placed = 0;
        int attempts = 0;
        int maximumAttempts = maximumPlacements * MAX_ATTEMPTS_PER_PLACEMENT;
        while (placed < maximumPlacements && attempts++ < maximumAttempts) {
            int cursor = state.nextFloodCursor();
            int localX = cursor & 15;
            int localZ = cursor >>> 4;
            int worldX = chunk.getPos().getMinBlockX() + localX;
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            int targetY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
            BlockPos target = new BlockPos(worldX, targetY, worldZ);
            if (targetY > representative.getY() + 1
                    || !safeTemporaryWaterTarget(level, chunk, target)
                    || !adjacentWater(level, water, target)) {
                continue;
            }
            var localFlow = WatershedServices.localFlow(level, target);
            if (!placeTrackedSurfaceWater(
                    level,
                    watersheds,
                    chunkKey,
                    state,
                    target,
                    SurfaceWaterKind.FLOOD,
                    WaterVolumeChunk.UNITS_PER_BLOCK,
                    localFlow.currentX(),
                    localFlow.currentZ()
            )) {
                continue;
            }
            placed++;
        }
        boolean countChanged = synchronizeStateCounts(state, ledger, chunkKey);
        if (placed > 0 || countChanged || attempts > 0) {
            watersheds.markChanged();
        }
        return placed;
    }

    /** Recedes exact tracked floodwater whose watershed has returned below risk. */
    public static int recede(
            ServerLevel level,
            WatershedSavedData watersheds,
            int maximumRemovals
    ) {
        if (maximumRemovals <= 0) {
            return 0;
        }
        TemporaryFloodSavedData ledger = TemporaryFloodSavedData.get(level);
        int maximumCandidates = Math.max(
                maximumRemovals,
                maximumRemovals * MAX_RECESSION_SCAN_MULTIPLIER
        );
        List<Long> candidates = ledger.recessionCandidates(maximumCandidates);
        int removed = 0;
        for (long packedPosition : candidates) {
            if (removed >= maximumRemovals) {
                break;
            }
            BlockPos position = BlockPos.of(packedPosition);
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    position.getX() >> 4,
                    position.getZ() >> 4
            );
            if (chunk == null) {
                continue;
            }
            long chunkKey = chunk.getPos().toLong();
            WatershedChunkState state = watersheds.state(chunkKey);
            WatershedConditions conditions = state == null
                    ? WatershedConditions.NONE
                    : state.conditions();
            SurfaceWaterKind kind = ledger.kind(packedPosition);
            long ageTicks = Math.max(0L, level.getGameTime() - ledger.placedTick(packedPosition));
            if (TransientSurfaceWaterModel.retains(
                    kind,
                    conditions,
                    ageTicks,
                    kind == SurfaceWaterKind.FLOOD
                            ? 0
                            : WaterSimulationConfig.surfaceWaterMinimumLifetimeTicks(),
                    WaterSimulationConfig.watershedPondFormationThreshold(),
                    WaterSimulationConfig.watershedWetlandFormationThreshold(),
                    WaterSimulationConfig.watershedSpringThreshold()
            )) {
                continue;
            }

            WaterVolumeChunk.WaterCell tracked = CanonicalWater.getTracked(level, position);
            int flags = tracked == null ? 0 : tracked.flags();
            boolean projection = WildernessWaterAuthority.isPlainWaterProjection(
                    level.getBlockState(position));
            if (TemporaryFloodSavedData.mayRemoveTrackedCell(true, flags, projection)) {
                if (CanonicalWater.removeTemporaryFlood(level, position)) {
                    restoreOriginalState(level, position, ledger.originalState(packedPosition));
                    ledger.forget(packedPosition);
                    removed++;
                }
            } else {
                // The player, another mod, or ordinary canonical flow changed
                // the position. Drop only our stale ledger claim.
                ledger.forget(packedPosition);
            }
            if (state != null && synchronizeStateCounts(state, ledger, chunkKey)) {
                watersheds.markChanged();
            }
        }
        return removed;
    }

    /** Places and records one exact reversible cell, rolling back if the ledger rejects it. */
    static boolean placeTrackedSurfaceWater(
            ServerLevel level,
            WatershedSavedData watersheds,
            long chunkKey,
            WatershedChunkState state,
            BlockPos target,
            SurfaceWaterKind kind,
            int volumeUnits,
            float velocityX,
            float velocityZ
    ) {
        if (state == null || kind == null || kind == SurfaceWaterKind.NONE) {
            return false;
        }
        BlockState originalState = level.getBlockState(target);
        if (!CanonicalWater.placeTemporarySurfaceWater(
                level,
                target,
                volumeUnits,
                velocityX,
                velocityZ
        )) {
            return false;
        }
        TemporaryFloodSavedData ledger = TemporaryFloodSavedData.get(level);
        if (!ledger.record(
                target,
                state.conditions().basinId(),
                level.getGameTime(),
                WaterSimulationConfig.watershedMaxTransientWaterCells(),
                originalState,
                kind
        )) {
            CanonicalWater.removeTemporaryFlood(level, target);
            restoreOriginalState(level, target, originalState);
            return false;
        }
        if (synchronizeStateCounts(state, ledger, chunkKey)) {
            watersheds.markChanged();
        }
        return true;
    }

    /** Returns whether terrain is safe for any reversible watershed surface water. */
    static boolean safeTemporaryWaterTarget(
            ServerLevel level,
            LevelChunk chunk,
            BlockPos target
    ) {
        if (level.isOutsideBuildHeight(target)
                || level.getBlockEntity(target) != null
                || insideStructure(chunk, target)) {
            return false;
        }
        BlockState state = level.getBlockState(target);
        if (state.is(WatershedTags.FLOOD_PROTECTED)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isAir()) {
            return true;
        }
        return state.canBeReplaced() && state.is(WatershedTags.FLOOD_REPLACEABLE);
    }

    private static boolean insideStructure(LevelChunk chunk, BlockPos position) {
        return chunk.getAllStarts().values().stream()
                .anyMatch(start -> start.isValid() && start.getBoundingBox().isInside(position));
    }

    private static boolean adjacentWater(ServerLevel level, WaterAccess water, BlockPos target) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (water.isWaterAt(level, target.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    static void restoreOriginalState(
            ServerLevel level,
            BlockPos position,
            BlockState originalState
    ) {
        if (originalState == null || originalState.isAir()) {
            return;
        }
        BlockState current = level.getBlockState(position);
        if (current.isAir() && originalState.canSurvive(level, position)) {
            level.setBlock(position, originalState, 3);
        }
    }

    private static boolean synchronizeStateCounts(
            WatershedChunkState state,
            TemporaryFloodSavedData ledger,
            long chunkKey
    ) {
        boolean changed = state.setActiveFloodCells(
                ledger.countInChunk(chunkKey, SurfaceWaterKind.FLOOD)
        );
        changed |= state.setActiveSurfaceWaterCells(ledger.standingWaterCountInChunk(chunkKey));
        changed |= state.setDynamicWaterFeature(
                ledger.dominantStandingKind(chunkKey).waterFeature()
        );
        return changed;
    }
}
