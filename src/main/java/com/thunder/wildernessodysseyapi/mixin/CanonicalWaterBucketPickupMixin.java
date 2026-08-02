package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.CanonicalWaterBucketTransactions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Routes projected source pickup through the exact canonical transaction.
 *
 * <p>{@link LiquidBlock} has no event before it removes a source and returns a
 * bucket, so this mixin is the smallest safe point shared by player buckets and
 * dispenser automation. Vanilla-owned liquids are left untouched.</p>
 */
@Mixin(LiquidBlock.class)
public abstract class CanonicalWaterBucketPickupMixin {

    /** Commits or rejects owned water before vanilla can award a bucket. */
    @Inject(
            method = "pickupBlock(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void wildernessOdysseyApi$transactCanonicalBucket(
            @Nullable Player player,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        LiquidBlock liquidBlock = (LiquidBlock) (Object) this;
        CanonicalWaterBucketTransactions.PickupDecision decision =
                CanonicalWaterBucketTransactions.pickup(
                        serverLevel,
                        pos,
                        state,
                        liquidBlock.fluid
                );
        if (decision.handled()) {
            callbackInfo.setReturnValue(decision.bucket());
        }
    }
}
