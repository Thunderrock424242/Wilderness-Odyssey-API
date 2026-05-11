package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.neoforged.neoforge.common.SoundActions;

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

    /**
     * Inject into emptyContents. This is the method Vanilla uses right before a fluid block appears.
     */
    @Inject(
            method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onBucketEmpty(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result, @Nullable ItemStack container, CallbackInfoReturnable<Boolean> cir) {
        Fluid content = ((BucketItem) (Object) this).content;

        // Only intercept if the bucket actually contains WATER
        if (content == Fluids.WATER) {

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
            SoundEvent soundEvent = content.getFluidType().getSound(player, level, pos, SoundActions.BUCKET_EMPTY);
            if (soundEvent == null) soundEvent = SoundEvents.BUCKET_EMPTY;
            level.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);

            // CRITICAL: Cancel the original method!
            // This stops Vanilla from instantly placing a square water block over our simulation,
            // but still allows the game to give the player their empty bucket back safely.
            cir.setReturnValue(true);
        }
    }
}
