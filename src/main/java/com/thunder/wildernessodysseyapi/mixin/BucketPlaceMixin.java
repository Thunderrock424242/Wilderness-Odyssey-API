package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Creates a server-authoritative volumetric body after vanilla water placement.
 *
 * <p>Vanilla remains responsible for the persistent source block, inventory,
 * sounds, game events, permissions, and fluid-container behavior. The earlier
 * implementation cancelled placement and replaced the source with an SPH body;
 * that body could disappear because settled-particle block conversion is
 * intentionally disabled. The source remains as a safety fallback until SPH
 * persistence is enabled in the next water-system phase.</p>
 */
@Mixin(BucketItem.class)
public abstract class BucketPlaceMixin {

    /**
     * Spawns authoritative SPH only after vanilla confirms that placement worked.
     */
    @Inject(
            method = "emptyContents(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/phys/BlockHitResult;"
                    + "Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN")
    )
    private void onBucketEmpty(@Nullable Player player, Level level, BlockPos pos,
                               @Nullable BlockHitResult result, @Nullable ItemStack container,
                               CallbackInfoReturnable<Boolean> callbackInfo) {
        Fluid content = ((BucketItem) (Object) this).content;
        if (content != Fluids.WATER
                || level.isClientSide
                || !Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            return;
        }

        // Server physics owns collision and particle history. Clients receive
        // interpolated snapshots instead of creating a divergent local splash.
        SPHSimulationManager.get().createSimulation(
                pos.getX() + 0.5f,
                pos.getY() + 0.65f,
                pos.getZ() + 0.5f,
                level,
                settledPos -> level.setBlockAndUpdate(settledPos, Blocks.WATER.defaultBlockState())
        );
    }
}
