package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * Creates canonical volume and a server-authoritative SPH body after placement.
 *
 * <p>Vanilla remains responsible for inventory, sounds, game events, and
 * permissions. Its temporary source block is then removed because persistent
 * SPH owns the mobile bucket volume until it settles into canonical cells.</p>
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
        if (!level.getBlockState(pos).is(Blocks.WATER)) {
            // Waterlogged blocks remain on the vanilla compatibility boundary;
            // replacing their host block would destroy unrelated block state.
            return;
        }

        // Server physics owns collision and particle history. Clients receive
        // interpolated snapshots instead of creating a divergent local splash.
        SPHSimulationManager.BucketPlacementResult placement =
                SPHSimulationManager.get().createBucketSimulation(
                pos.getX() + 0.5f,
                pos.getY() + 0.65f,
                pos.getZ() + 0.5f,
                level,
                settledPos -> { }
        );

        // Vanilla completed permissions, inventory, sound, and game events.
        // Remove its duplicate source only when persistent SPH owns the bucket.
        // An overloaded manager deliberately leaves canonical projected water.
        ServerLevel serverLevel = (ServerLevel) level;
        if (placement.sphOwnsVolume()) {
            serverLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
