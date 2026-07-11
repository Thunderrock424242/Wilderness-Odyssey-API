package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.network.SphLocalEffectPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Converts successful bucket placement into canonical Wilderness water.
 *
 * <p>Vanilla remains responsible for inventory, sounds, game events, and
 * permissions. Once placement succeeds, the temporary vanilla source is
 * immediately rewritten through {@link CanonicalWater} so the durable block is
 * the mod's namespaced Wilderness water. SPH is only a visual splash here; it
 * must not be the sole owner of player-placed bucket volume.</p>
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
        if (!isCanonicalBucketWater(content)
                || level.isClientSide
                || !WildernessWaterRules.isEnabled(level)
                || !WaterSimulationConfig.vanillaBucketCompatEnabled()
                || !Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            return;
        }
        if (!WaterCompatibility.isPlainWaterProjection(level.getBlockState(pos))) {
            // Waterlogged blocks remain on the vanilla compatibility boundary;
            // replacing their host block would destroy unrelated block state.
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // Player buckets should become the Wilderness water system
        // immediately. This writes canonical volume and projects the placed
        // source to wildernessodysseyapi:wilderness_water_block for tag
        // compatibility and future bucket pickup.
        CanonicalWater.placeBucket(serverLevel, pos);

        // SPH still gives the bucket a nice splash, but as a compact client
        // event rather than a server-owned particle stream. The canonical cell
        // above remains after the local particles expire.
        float splashX = pos.getX() + 0.5f;
        float splashY = pos.getY() + 0.65f;
        float splashZ = pos.getZ() + 0.5f;
        SphLocalEffectPayload.sendToNearby(
                serverLevel,
                splashX,
                splashY,
                splashZ,
                SphLocalEffectPayload.bucketSplash(splashX, splashY, splashZ)
        );
    }

    private static boolean isCanonicalBucketWater(Fluid fluid) {
        return WaterCompatibility.isCanonicalWaterFluid(fluid);
    }
}
