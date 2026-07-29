package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterInteractionResult;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BooleanSupplier;

/**
 * Reconciles direct projected-block writes with canonical water authority.
 *
 * <p>Some machines, including Create open-ended pipes, place or remove fluid
 * blocks without querying a block capability. This boundary runs immediately
 * before {@link Level#setBlock(BlockPos, BlockState, int, int)} and only does
 * work when the old or new state is the standalone Wilderness liquid block.
 * An exact authority commit owns the physical projection and intercepts the
 * original write, preventing duplicate writes and false failure results after
 * canonical projection has already reached the target state.</p>
 */
public final class WorldFluidMutationReconciler {

    private static final ThreadLocal<Integer> RECONCILIATION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static volatile boolean active;

    private WorldFluidMutationReconciler() {
    }

    /** Activates the mixin boundary after the NeoForge compatibility adapter initializes. */
    public static void activate() {
        active = true;
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
            return level.setBlock(position, state, flags);
        } finally {
            restoreDepth(previousDepth);
        }
    }

    /**
     * Decides whether one server block write should continue or be intercepted.
     *
     * <p>An exact authority transfer returns {@link MutationDecision#COMMITTED}
     * because canonical projection has already performed the physical write.
     * Failed transfers return {@link MutationDecision#REJECTED}; irrelevant,
     * no-delta, disabled, and recursive projection calls continue normally.</p>
     */
    public static MutationDecision beforeSetBlock(
            Level level,
            BlockPos position,
            BlockState newState
    ) {
        if (!active
                || RECONCILIATION_DEPTH.get() > 0
                || !(level instanceof ServerLevel serverLevel)) {
            return MutationDecision.CONTINUE;
        }
        return reconcile(
                serverLevel,
                position,
                newState,
                WaterServices.access(),
                WaterSimulationConfig::fluidHandlerCompatEnabled
        );
    }

    static MutationDecision reconcile(
            ServerLevel level,
            BlockPos position,
            BlockState newState,
            WaterAccess waterAccess,
            BooleanSupplier compatibilityEnabled
    ) {
        if (!compatibilityEnabled.getAsBoolean()
                || level.isDebug()
                || level.isOutsideBuildHeight(position)
                || !level.hasChunkAt(position)) {
            return MutationDecision.CONTINUE;
        }

        BlockState oldState = level.getBlockState(position);
        boolean oldProjection = isWildernessProjection(oldState);
        boolean newProjection = isWildernessProjection(newState);
        if (!oldProjection && !newProjection) {
            return MutationDecision.CONTINUE;
        }

        // Only an air replacement represents a direct fluid extraction. Solid
        // and other-fluid replacements must continue to the normal placement
        // path so displacement logic can preserve both the block and the water.
        if (oldProjection && !newProjection && !newState.isAir()) {
            return MutationDecision.CONTINUE;
        }
        if (!level.getServer().isSameThread()) {
            ModConstants.LOGGER.warn(
                    "Rejected off-thread Wilderness water projection mutation at {}",
                    position
            );
            return MutationDecision.REJECTED;
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
            return MutationDecision.CONTINUE;
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
