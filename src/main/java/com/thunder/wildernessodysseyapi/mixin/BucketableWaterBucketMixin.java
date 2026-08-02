package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets Wilderness water buckets collect vanilla bucketable aquatic mobs.
 *
 * <p>{@link Bucketable#bucketMobPickup} uses exact item identity rather than a
 * fluid or item tag and offers no compatibility event. This narrow mixin maps
 * the custom bucket to {@link Items#WATER_BUCKET} only for that comparison;
 * vanilla still owns mob data serialization, criteria, sounds, inventory
 * replacement, and entity removal.</p>
 */
@Mixin(Bucketable.class)
public interface BucketableWaterBucketMixin {

    /** Presents the custom bucket as vanilla water for the single pickup gate. */
    @WrapOperation(
            method = "bucketMobPickup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getItem()"
                            + "Lnet/minecraft/world/item/Item;"
            ),
            require = 1
    )
    private static Item wildernessOdysseyApi$acceptWildernessWaterBucket(
            ItemStack stack,
            Operation<Item> original
    ) {
        Item actualItem = original.call(stack);
        return actualItem == WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get()
                ? Items.WATER_BUCKET
                : actualItem;
    }
}
