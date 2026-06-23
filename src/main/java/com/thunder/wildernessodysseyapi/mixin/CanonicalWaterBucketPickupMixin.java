package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/** Drains canonical volume when vanilla successfully picks up a water source. */
@Mixin(LiquidBlock.class)
public abstract class CanonicalWaterBucketPickupMixin {

    /** Runs before vanilla removes the source so legacy water can be imported exactly once. */
    @Inject(method = "pickupBlock", at = @At("HEAD"))
    private void wildernessOdysseyApi$drainCanonicalBucket(
            @Nullable Player player,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        LiquidBlock liquidBlock = (LiquidBlock) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)
                || !liquidBlock.fluid.isSame(Fluids.WATER)
                || state.getValue(BlockStateProperties.LEVEL) != 0) {
            return;
        }
        CanonicalWater.drainVolume(serverLevel, pos, WaterVolumeChunk.UNITS_PER_BLOCK);
    }
}
