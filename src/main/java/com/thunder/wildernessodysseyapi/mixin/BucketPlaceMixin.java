package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BucketPlaceMixin
 *
 * Intercepts BucketItem#useOn to detect when a water bucket is placed.
 * On placement:
 *   - Spawns an SPH simulation at the placement position (client)
 *   - Cancels the vanilla block placement so only the simulation runs
 *   - The simulation's settle callback places real water blocks later
 */
@Mixin(BucketItem.class)
public class BucketPlaceMixin {

    @Inject(
        method = "useOn",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBucketUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BucketItem self = (BucketItem)(Object)this;

        // Only intercept water buckets
        if (!self.content.isSame(Fluids.WATER)) return;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Player player = context.getPlayer();

        if (player == null) return;

        float cx = pos.getX() + 0.5f;
        float cy = pos.getY() + 0.9f; // spawn slightly above block centre
        float cz = pos.getZ() + 0.5f;

        if (level.isClientSide()) {
            // Client: spawn visual simulation
            SPHSimulationManager.get().createSimulation(
                cx, cy, cz,
                level,
                blockPos -> {
                    // No-op on client — server handles real block placement
                }
            );
        } else {
            // Server: spawn simulation whose settle callback places real fluid blocks
            SPHSimulationManager.get().createSimulation(
                cx, cy, cz,
                level,
                blockPos -> {
                    if (level.getBlockState(blockPos).isAir()) {
                        level.setBlock(blockPos,
                            net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
                    }
                }
            );
        }

        // Consume the bucket item
        if (!player.isCreative()) {
            player.getInventory().removeItem(context.getItemInHand());
            player.getInventory().add(
                new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.BUCKET));
        }

        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide()));
    }
}
