package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterInteractionResult;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.tags.FluidTags;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Reconciles direct projected-block writes with canonical water authority.
 *
 * <p>Some machines, including Create open-ended pipes, place or remove fluid
 * blocks without querying a block capability. This boundary runs immediately
 * before {@link Level#setBlock(BlockPos, BlockState, int, int)} and only does
 * runtime work when the old or new state is the standalone Wilderness liquid block.
 * An exact authority commit owns the physical projection and intercepts the
 * original write, preventing duplicate writes and false failure results after
 * canonical projection has already reached the target state. Initial starter
 * construction has a separate bounded generation scope that also removes
 * obsolete sparse overrides when its final terrain is stored.</p>
 */
public final class WorldFluidMutationReconciler {

    private static final ThreadLocal<Integer> RECONCILIATION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<StarterTerrainContext> STARTER_TERRAIN = new ThreadLocal<>();
    private static volatile boolean active;

    private WorldFluidMutationReconciler() {
    }

    /** Activates the mixin boundary after the NeoForge compatibility adapter initializes. */
    public static void activate() {
        active = true;
    }

    /** Prevents accepted-terrain observation from feeding internal water/generation writes back into flow. */
    public static boolean suppressesTerrainWake(ServerLevel level, BlockPos position) {
        StarterTerrainContext generation = STARTER_TERRAIN.get();
        return RECONCILIATION_DEPTH.get() > 0
                || (generation != null && generation.level() == level && generation.bounds().isInside(position));
    }

    /**
     * Runs initial starter-island construction using final generation state.
     *
     * <p>The starter is built through ServerLevel after its selected chunks are
     * generated. Treating its ocean fill as runtime solid placement would push
     * the removed ocean onto the island and retain hidden parcels in its rooms.
     * Only synchronous writes in this level and footprint use generation
     * semantics; nested calls restore their owner, including on failure.</p>
     */
    public static <T> T withStarterTerrainGeneration(ServerLevel level, BoundingBox bounds, Supplier<T> generation) {
        if (!level.getServer().isSameThread() || level.getServer().isStopped()) {
            throw new IllegalStateException("Starter terrain generation requires the active server thread");
        }
        StarterTerrainContext previous = STARTER_TERRAIN.get();
        STARTER_TERRAIN.set(new StarterTerrainContext(level, new BoundingBox(
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())));
        try {
            return generation.get();
        } finally {
            if (previous == null) STARTER_TERRAIN.remove();
            else STARTER_TERRAIN.set(previous);
        }
    }

    /**
     * Writes a block projection while marking it as canonical-authority output.
     *
     * <p>Only the focused {@code CanonicalWater.projectCompatibility} redirect
     * calls this method. The guard prevents the global block-write hook from
     * converting an internal render projection into another water transfer.</p>
     */
    public static boolean setCanonicalProjectionBlock(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            int flags
    ) {
        int previousDepth = RECONCILIATION_DEPTH.get();
        RECONCILIATION_DEPTH.set(previousDepth + 1);
        try {
            return WaterMutationSafety.setBlock(level, position, state, flags);
        } finally {
            restoreDepth(previousDepth);
        }
    }

    /**
     * Decides whether one server block write should continue or be intercepted.
     *
     * <p>An exact authority transfer returns {@link MutationDecision#COMMITTED}
     * because canonical projection has already performed the physical write.
     * Failed transfers return {@link MutationDecision#REJECTED}; relevant direct
     * writes without a transfer still use the loaded shape context. Irrelevant
     * and recursive projection calls continue normally.</p>
     */
    public static MutationDecision beforeSetBlock(
            Level level,
            BlockPos position,
            BlockState newState,
            int flags,
            int recursionLeft
    ) {
        if (!active
                || RECONCILIATION_DEPTH.get() > 0
                || !(level instanceof ServerLevel serverLevel)) {
            return MutationDecision.CONTINUE;
        }
        StarterTerrainContext generation = STARTER_TERRAIN.get();
        if (generation != null && generation.level() == serverLevel && generation.bounds().isInside(position)
                && WildernessWaterRules.isEnabled(serverLevel)
                && !serverLevel.captureBlockSnapshots && !serverLevel.restoringBlockSnapshots) {
            return reconcileStarterTerrain(serverLevel, position, newState, flags, recursionLeft);
        }
        return reconcile(
                serverLevel,
                position,
                newState,
                WaterServices.access(),
                WaterSimulationConfig::fluidHandlerCompatEnabled,
                flags,
                recursionLeft
        );
    }

    private static MutationDecision reconcileStarterTerrain(ServerLevel level, BlockPos position,
            BlockState requestedState, int flags, int recursionLeft) {
        if (level.isOutsideBuildHeight(position)) return MutationDecision.REJECTED;
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return MutationDecision.REJECTED;
        BlockState mapped = GenerationWaterStateMapper.map(requestedState);
        var volume = chunk.getExistingData(ModAttachments.WATER_VOLUME);
        var generated = chunk.getExistingData(ModAttachments.GENERATED_WATER);
        if (!isWildernessProjection(chunk.getBlockState(position)) && !isWildernessProjection(mapped)
                && (volume.isEmpty() || !volume.get().contains(position))
                && (generated.isEmpty() || generated.get().spanAt(position) == null)) {
            return MutationDecision.CONTINUE;
        }

        int previousDepth = RECONCILIATION_DEPTH.get();
        RECONCILIATION_DEPTH.set(previousDepth + 1);
        try {
            // The constructor already owns generation of its selected columns.
            // Require the target chunk, but never generate callback neighbors:
            // the loaded shape view supplies the same bounded boundary used by
            // water projection. Normal flags and block callbacks remain intact.
            boolean changed = WaterMutationSafety.withLoadedShapeContext(level,
                    () -> level.setBlock(position, mapped, flags, recursionLeft));
            BlockState stored = chunk.getBlockState(position);
            if (!changed && !stored.equals(mapped)) return MutationDecision.REJECTED;

            volume.ifPresent(water -> water.set(position, WaterVolumeChunk.WaterCell.EMPTY));
            GenerationWaterStateMapper.recordStoredState(chunk, position, stored);
            chunk.setUnsaved(true);
            return changed ? MutationDecision.COMMITTED : MutationDecision.REJECTED;
        } finally {
            restoreDepth(previousDepth);
        }
    }

    private record StarterTerrainContext(ServerLevel level, BoundingBox bounds) { }

    static MutationDecision reconcile(
            ServerLevel level,
            BlockPos position,
            BlockState newState,
            WaterAccess waterAccess,
            BooleanSupplier compatibilityEnabled
    ) {
        return reconcile(level, position, newState, waterAccess, compatibilityEnabled, 3, 512);
    }

    private static MutationDecision reconcile(ServerLevel level, BlockPos position, BlockState newState,
            WaterAccess waterAccess, BooleanSupplier compatibilityEnabled, int flags, int recursionLeft) {
        if (!WildernessWaterRules.isEnabled(level)
                || level.isDebug()
                || level.isOutsideBuildHeight(position)
                || !level.hasChunkAt(position)
                || level.captureBlockSnapshots
                || level.restoringBlockSnapshots) {
            return MutationDecision.CONTINUE;
        }

        BlockState oldState = level.getBlockState(position);
        boolean oldProjection = isWildernessProjection(oldState);
        boolean newProjection = isWildernessProjection(newState);
        if (!oldProjection && !newProjection) {
            return MutationDecision.CONTINUE;
        }

        if (!level.getServer().isSameThread()) {
            ModConstants.LOGGER.warn(
                    "Rejected off-thread Wilderness water projection mutation at {}",
                    position
            );
            return MutationDecision.REJECTED;
        }
        if (!WaterMutationSafety.isReady(level, position, WaterMutationSafety.CALLBACK_RADIUS)) {
            return MutationDecision.REJECTED;
        }

        // Provisional player placements are handled by the accepted placement
        // event. Direct writes have no such event: commit the solid first, then
        // conserve its displaced parcel. A failed block write changes no volume.
        if (oldProjection && !newProjection && !newState.isAir()
                && !newState.canBeReplaced() && !newState.getFluidState().is(FluidTags.WATER)
                && !newState.getCollisionShape(level, position).isEmpty()) {
            int previousDepth = RECONCILIATION_DEPTH.get();
            RECONCILIATION_DEPTH.set(previousDepth + 1);
            try {
                if (!WaterMutationSafety.withLoadedShapeContext(level,
                        () -> level.setBlock(position, newState, flags, recursionLeft))) {
                    return MutationDecision.REJECTED;
                }
                CanonicalWater.displaceForSolidPlacement(level, position, oldState, newState);
                WildernessFluidRegistry.notifyTerrainChanged(level, position);
                return MutationDecision.COMMITTED;
            } finally {
                restoreDepth(previousDepth);
            }
        }
        if (!compatibilityEnabled.getAsBoolean()
                || (oldProjection && !newProjection && !newState.isAir())) {
            return commitPhysicalWrite(level, position, newState, flags, recursionLeft);
        }

        WildernessWaterAuthority.CellAuthority authority =
                WildernessWaterAuthority.sample(level, position);
        long currentUnits = authority.water() && authority.authorityOwned()
                ? authority.volumeUnits()
                : 0L;
        long targetUnits = newProjection
                ? WildernessWaterAuthority.volumeUnitsFromFluid(newState.getFluidState())
                : 0L;
        if (currentUnits == targetUnits) {
            // A deferred cell can already own the target volume while its old
            // physical projection differs. Protect that no-delta write too.
            return oldState.equals(newState) ? MutationDecision.CONTINUE
                    : commitPhysicalWrite(level, position, newState, flags, recursionLeft);
        }

        long deltaUnits = Math.abs(targetUnits - currentUnits);
        boolean adding = targetUnits > currentUnits;
        if (!canTransferExactly(waterAccess, level, position, deltaUnits, adding)) {
            ModConstants.LOGGER.warn(
                    "Rejected Wilderness water projection mutation at {} because authority could not {} {} units",
                    position,
                    adding ? "accept" : "remove",
                    deltaUnits
            );
            return MutationDecision.REJECTED;
        }

        int previousDepth = RECONCILIATION_DEPTH.get();
        RECONCILIATION_DEPTH.set(previousDepth + 1);
        try {
            WaterInteractionResult executed = adding
                    ? waterAccess.addWater(level, position, deltaUnits, false)
                    : waterAccess.removeWater(level, position, deltaUnits, false);
            if (executed.transferredUnits() == deltaUnits) {
                return MutationDecision.COMMITTED;
            }
            if (executed.transferredUnits() > 0L) {
                rollbackPartialTransfer(
                        waterAccess,
                        level,
                        position,
                        executed.transferredUnits(),
                        adding
                );
            }
            return MutationDecision.REJECTED;
        } finally {
            restoreDepth(previousDepth);
        }
    }

    private static MutationDecision commitPhysicalWrite(ServerLevel level, BlockPos position,
            BlockState newState, int flags, int recursionLeft) {
        int previousDepth = RECONCILIATION_DEPTH.get();
        RECONCILIATION_DEPTH.set(previousDepth + 1);
        try {
            return WaterMutationSafety.withLoadedShapeContext(level,
                    () -> level.setBlock(position, newState, flags, recursionLeft))
                    ? MutationDecision.COMMITTED : MutationDecision.REJECTED;
        } finally {
            restoreDepth(previousDepth);
        }
    }

    private static boolean canTransferExactly(
            WaterAccess waterAccess,
            ServerLevel level,
            BlockPos position,
            long deltaUnits,
            boolean adding
    ) {
        WaterInteractionResult simulated = adding
                ? waterAccess.addWater(level, position, deltaUnits, true)
                : waterAccess.removeWater(level, position, deltaUnits, true);
        return simulated.transferredUnits() == deltaUnits;
    }

    // Restores the pre-write authority state when execution unexpectedly
    // differs from the exact amount negotiated during simulation.
    private static void rollbackPartialTransfer(
            WaterAccess waterAccess,
            ServerLevel level,
            BlockPos position,
            long transferredUnits,
            boolean adding
    ) {
        WaterInteractionResult rollback = adding
                ? waterAccess.removeWater(level, position, transferredUnits, false)
                : waterAccess.addWater(level, position, transferredUnits, false);
        if (rollback.transferredUnits() != transferredUnits) {
            ModConstants.LOGGER.error(
                    "Failed to roll back partial Wilderness projection mutation at {}: restored {} of {} units",
                    position,
                    rollback.transferredUnits(),
                    transferredUnits
            );
        }
    }

    private static boolean isWildernessProjection(BlockState state) {
        return state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
    }

    private static void restoreDepth(int previousDepth) {
        if (previousDepth == 0) {
            RECONCILIATION_DEPTH.remove();
        } else {
            RECONCILIATION_DEPTH.set(previousDepth);
        }
    }

    /**
     * Describes how the {@code Level#setBlock} mixin should finish the call.
     *
     * <p>{@link #CONTINUE} leaves vanilla execution untouched. {@link #COMMITTED}
     * and {@link #REJECTED} both cancel the original method and return success
     * or failure respectively.</p>
     */
    public enum MutationDecision {
        CONTINUE,
        COMMITTED,
        REJECTED;

        /** Returns whether the mixin must cancel the original block write. */
        public boolean interceptsOriginal() {
            return this != CONTINUE;
        }

        /** Returns the value to expose when the original write is intercepted. */
        public boolean returnValue() {
            return this == COMMITTED;
        }
    }
}
