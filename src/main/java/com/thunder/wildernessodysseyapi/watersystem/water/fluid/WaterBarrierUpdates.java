package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.reactive.ReactiveWaterController;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Admits accepted dry-terrain changes into the existing local water wake path.
 * This observer owns no water or work queue and never projects blocks itself.
 */
public final class WaterBarrierUpdates {

    private static final Direction[] NEIGHBORS = Direction.values();

    private WaterBarrierUpdates() { }

    /**
     * Handles an actual stored chunk change, including automation writes that
     * intentionally omit Minecraft neighbor notifications. Ambiguous solid
     * hosts remain closed under the existing canonical occupancy predicate.
     */
    public static void onBlockChanged(LevelChunk chunk, BlockPos position,
                                     BlockState previousState, BlockState newState) {
        if (previousState == null || previousState == newState
                || !previousState.getFluidState().isEmpty() || !newState.getFluidState().isEmpty()
                || passable(previousState) == passable(newState)) {
            return;
        }
        if (!(chunk.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || level.getServer().isStopped()
                || level.captureBlockSnapshots || level.restoringBlockSnapshots
                || !WildernessWaterRules.isEnabled(level)
                || WorldFluidMutationReconciler.suppressesTerrainWake(level, position)
                || level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk
                || !chunk.getBlockState(position).equals(newState)) {
            return;
        }
        ReactiveWaterController.invalidateTerrain(level, position);
        if (ownedWaterAt(level, chunk, position)) {
            WildernessFluidRegistry.notifyTerrainChanged(level, position);
            return;
        }
        for (Direction direction : NEIGHBORS) {
            if (ownedWaterAt(level, chunk, position.relative(direction))) {
                WildernessFluidRegistry.notifyTerrainChanged(level, position);
                return;
            }
        }
    }

    private static boolean passable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean ownedWaterAt(ServerLevel level, LevelChunk changedChunk, BlockPos position) {
        if (level.isOutsideBuildHeight(position)) return false;
        LevelChunk chunk = changedChunk.getPos().x == (position.getX() >> 4)
                && changedChunk.getPos().z == (position.getZ() >> 4)
                ? changedChunk
                : level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return false;
        var sparse = chunk.getExistingData(ModAttachments.WATER_VOLUME);
        if (sparse.isPresent() && sparse.get().contains(position)) {
            WaterVolumeChunk.WaterCell cell = sparse.get().get(position);
            return cell.projectionPending()
                    || (cell.volumeUnits() > 0 && !cell.imported() && !cell.hostedWater());
        }
        var generated = chunk.getExistingData(ModAttachments.GENERATED_WATER);
        if (generated.isEmpty()) return false;
        GeneratedWaterChunk.WaterSpan span = generated.get().spanAt(position);
        if (span == null) return false;
        var authority = WildernessWaterAuthority.sample(level, position);
        return authority.water() && authority.authorityOwned();
    }
}
