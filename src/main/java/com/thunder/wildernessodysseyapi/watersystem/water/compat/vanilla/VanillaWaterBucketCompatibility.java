package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Gives the namespaced Wilderness water bucket vanilla container behavior.
 *
 * <p>Minecraft registers dispenser and cauldron behavior by exact item identity,
 * so a tagged custom water bucket does not inherit these paths automatically.
 * This adapter registers the existing bucket with the same interaction and
 * remainder semantics as {@link Items#WATER_BUCKET} after registries are ready.</p>
 */
public final class VanillaWaterBucketCompatibility {

    static final DispenseItemBehavior WILDERNESS_WATER_BUCKET_DISPENSE_BEHAVIOR =
            new WildernessWaterBucketDispenseBehavior();

    private static boolean registered;

    private VanillaWaterBucketCompatibility() {
    }

    /**
     * Registers dispenser and cauldron parity for the Wilderness water bucket.
     *
     * <p>Common setup may be exercised more than once by development harnesses,
     * so registration is synchronized and idempotent. Calling this method
     * requires deferred item registration to have completed.</p>
     */
    public static synchronized void bootstrap() {
        if (registered) {
            return;
        }

        Item waterBucket = WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get();

        // Dispensers use an exact-item lookup rather than the fluid's water tag.
        DispenserBlock.registerBehavior(waterBucket, WILDERNESS_WATER_BUCKET_DISPENSE_BEHAVIOR);

        // Every cauldron state accepts a vanilla water bucket, replacing its
        // contents with a full water cauldron and returning an empty bucket.
        CauldronInteraction.EMPTY.map().put(waterBucket, CauldronInteraction.FILL_WATER);
        CauldronInteraction.WATER.map().put(waterBucket, CauldronInteraction.FILL_WATER);
        CauldronInteraction.LAVA.map().put(waterBucket, CauldronInteraction.FILL_WATER);
        CauldronInteraction.POWDER_SNOW.map().put(waterBucket, CauldronInteraction.FILL_WATER);

        registered = true;
    }

    /** Mirrors vanilla's full-bucket behavior, including fallback ejection. */
    private static final class WildernessWaterBucketDispenseBehavior extends DefaultDispenseItemBehavior {

        private final DefaultDispenseItemBehavior fallback = new DefaultDispenseItemBehavior();

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            if (!(stack.getItem() instanceof DispensibleContainerItem containerItem)) {
                return fallback.dispense(source, stack);
            }

            BlockPos targetPos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            Level level = source.level();
            if (!containerItem.emptyContents(null, level, targetPos, null, stack)) {
                return fallback.dispense(source, stack);
            }

            containerItem.checkExtraContent(null, level, stack, targetPos);
            return consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
        }
    }
}
