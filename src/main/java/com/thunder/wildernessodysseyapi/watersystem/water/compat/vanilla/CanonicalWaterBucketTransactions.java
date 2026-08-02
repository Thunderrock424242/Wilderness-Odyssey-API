package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;

/**
 * Owns exact player and automation bucket pickup transactions.
 *
 * <p>A projected fluid source can visually look full before its fixed-point
 * cell contains a complete bucket. Vanilla would remove that projection and
 * award a 1,000 mB bucket anyway. This boundary imports the authoritative cell,
 * requires exactly one block of non-hosted water, drains that amount first, and
 * only then lets the caller receive a bucket. Unowned vanilla water continues
 * through Minecraft's normal implementation.</p>
 */
public final class CanonicalWaterBucketTransactions {

    /** Fixed-point authority volume represented by one vanilla water bucket. */
    public static final int BUCKET_VOLUME_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK;

    private CanonicalWaterBucketTransactions() {
    }

    /**
     * Attempts to pick up an authority-owned projected source atomically.
     *
     * <p>Successful canonical pickup deliberately returns a vanilla water
     * bucket. This is the broadest compatibility facade for mods that still
     * compare exact item identity instead of checking fluid tags. The registered
     * Wilderness bucket remains available to fluid handlers and has its own
     * dispenser, cauldron, waterlogging, and aquatic-mob parity.</p>
     *
     * @param level logical server that owns the authoritative cell
     * @param position source position requested by a player or dispenser
     * @param state projected liquid state passed to {@code LiquidBlock}
     * @param projectionFluid fluid owned by the targeted liquid block
     * @return whether vanilla should continue, reject, or use a committed bucket
     */
    public static PickupDecision pickup(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            Fluid projectionFluid
    ) {
        if (!WildernessWaterRules.isEnabled(level)
                || !WaterCompatibility.isCanonicalWaterFluid(projectionFluid)
                || !state.hasProperty(BlockStateProperties.LEVEL)
                || state.getValue(BlockStateProperties.LEVEL) != 0) {
            return PickupDecision.continueVanilla();
        }

        // Wilderness projections and generated spans are materialized before
        // the decision. A normal vanilla source is intentionally not imported.
        CanonicalWater.getOrImport(level, position);
        WildernessWaterAuthority.CellAuthority authority =
                WildernessWaterAuthority.sample(level, position);
        if (!authority.authorityOwned()) {
            return PickupDecision.continueVanilla();
        }
        if (!WaterSimulationConfig.vanillaBucketCompatEnabled()) {
            // Disabling translation must never expose a projected finite cell
            // to vanilla's unconditional source removal and bucket award.
            return PickupDecision.rejected();
        }
        if (!WildernessWaterAuthority.canBucketPickup(level, position)) {
            return PickupDecision.rejected();
        }

        WaterVolumeChunk.WaterCell before = CanonicalWater.get(level, position);
        int drained = CanonicalWater.drainVolume(level, position, BUCKET_VOLUME_UNITS);
        if (drained != BUCKET_VOLUME_UNITS) {
            // Server interactions are single-threaded, but restoring the exact
            // cell keeps this transaction lossless if another hook ever changes
            // authority between negotiation and execution.
            if (drained > 0 && before.volumeUnits() > 0) {
                CanonicalWater.set(level, position, before, true);
            }
            return PickupDecision.rejected();
        }

        return PickupDecision.committed(new ItemStack(Items.WATER_BUCKET));
    }

    /**
     * Returns whether a full bucket placement would overwrite owned volume.
     *
     * <p>Vanilla permits a fluid bucket to replace any non-solid liquid block.
     * That is harmless for infinite vanilla sources, but it would consume a
     * whole finite bucket without adding a whole block to an already-wet
     * canonical cell. Invalid solid targets are left to vanilla so its normal
     * adjacent-position retry still works.</p>
     */
    public static boolean wouldOverwriteOwnedWater(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            Fluid bucketFluid
    ) {
        if (!WildernessWaterRules.isEnabled(level)
                || !WaterCompatibility.isCanonicalWaterFluid(bucketFluid)
                || (!state.isAir() && !state.canBeReplaced(bucketFluid))) {
            return false;
        }

        CanonicalWater.getOrImport(level, position);
        WildernessWaterAuthority.CellAuthority authority =
                WildernessWaterAuthority.sample(level, position);
        return authority.authorityOwned() && authority.volumeUnits() > 0;
    }

    /** Returns whether an authority amount can fund exactly one full bucket. */
    public static boolean isExactBucketVolume(int volumeUnits) {
        return volumeUnits == BUCKET_VOLUME_UNITS;
    }

    /** Result consumed by the narrow {@code LiquidBlock} mixin boundary. */
    public record PickupDecision(Outcome outcome, ItemStack bucket) {

        /** Creates a validated immutable decision. */
        public PickupDecision {
            bucket = bucket == null ? ItemStack.EMPTY : bucket;
        }

        /** Returns whether the mixin must bypass vanilla pickup. */
        public boolean handled() {
            return outcome != Outcome.CONTINUE_VANILLA;
        }

        private static PickupDecision continueVanilla() {
            return new PickupDecision(Outcome.CONTINUE_VANILLA, ItemStack.EMPTY);
        }

        private static PickupDecision rejected() {
            return new PickupDecision(Outcome.REJECTED, ItemStack.EMPTY);
        }

        private static PickupDecision committed(ItemStack bucket) {
            return new PickupDecision(Outcome.COMMITTED, bucket);
        }
    }

    /** Describes ownership of the method return at the vanilla boundary. */
    public enum Outcome {
        CONTINUE_VANILLA,
        REJECTED,
        COMMITTED
    }
}
