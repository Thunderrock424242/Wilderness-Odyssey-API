package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * BucketPlaceMixin
 * <p>
 * Intercepts BucketItem#emptyContents to detect when a water bucket is poured.
 * On placement:
 * - Spawns an SPH simulation at the placement position
 * - Cancels the vanilla static block placement
 * - Allows vanilla to handle the empty bucket inventory math safely
 */
@Mixin(BucketItem.class)
public abstract class BucketPlaceMixin {

    // 1. Shadow the bucket's fluid type so we can check if it is WATER
    @Shadow @Final public Fluid content;

    // 2. Shadow the sound method so the game still plays the splash sound
    @Shadow protected abstract void playEmptySound(@Nullable Player player, LevelAccessor level, BlockPos pos);

    /**
     * Inject into emptyContents. This is the method Vanilla uses right before a fluid block appears.
     */
    @Inject(
            method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onBucketEmpty(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result, @Nullable ItemStack container, CallbackInfoReturnable<Boolean> cir) {

        // Only intercept if the bucket actually contains WATER
        if (this.content == Fluids.WATER) {

            // Only run the heavy SPH simulation on the logical server to prevent desyncs
            if (!level.isClientSide) {

                // Trigger your threaded SPH Manager!
                // Add 0.5f so the particles spawn exactly in the center of the block space
                SPHSimulationManager.get().createSimulation(
                        pos.getX() + 0.5f,
                        pos.getY() + 0.5f,
                        pos.getZ() + 0.5f,
                        level,
                        settlePos -> {
                            // Callback: When the fluid particles settle, place the real block
                            level.setBlock(settlePos, Blocks.WATER.defaultBlockState(), 3);
                        }
                );
            }

            // Play the vanilla pouring sound so it feels normal to the player
            this.playEmptySound(player, level, pos);

            // CRITICAL: Cancel the original method!
            // This stops Vanilla from instantly placing a square water block over our simulation,
            // but still allows the game to give the player their empty bucket back safely.
            cir.setReturnValue(true);
        }
    }
}