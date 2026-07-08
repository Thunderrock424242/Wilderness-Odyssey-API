package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/** Drains canonical volume when vanilla successfully picks up a water source. */
@Mixin(LiquidBlock.class)
public abstract class CanonicalWaterBucketPickupMixin {

    // Bucket pickup can be invoked by automation on different server threads.
    // Keep the imported pre-state scoped to the current invocation.
    private static final ThreadLocal<ServerLevel> PENDING_LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> PENDING_POS = new ThreadLocal<>();

    /** Imports legacy water before vanilla removes the source, without draining it yet. */
    @Inject(method = "pickupBlock", at = @At("HEAD"))
    private void wildernessOdysseyApi$captureCanonicalBucket(
            @Nullable Player player,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        PENDING_LEVEL.remove();
        PENDING_POS.remove();
        LiquidBlock liquidBlock = (LiquidBlock) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)
                || !WildernessWaterRules.isEnabled(serverLevel)
                || !isCanonicalPickupWater(liquidBlock)
                || state.getValue(BlockStateProperties.LEVEL) != 0) {
            return;
        }
        CanonicalWater.getOrImport(serverLevel, pos);
        PENDING_LEVEL.set(serverLevel);
        PENDING_POS.set(pos.immutable());
    }

    /** Drains exact volume only after vanilla confirms a water bucket was produced. */
    @Inject(method = "pickupBlock", at = @At("RETURN"))
    private void wildernessOdysseyApi$commitCanonicalBucket(
            @Nullable Player player,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        ServerLevel pendingLevel = PENDING_LEVEL.get();
        BlockPos pendingPos = PENDING_POS.get();
        PENDING_LEVEL.remove();
        PENDING_POS.remove();
        ItemStack result = callbackInfo.getReturnValue();
        if (pendingLevel == null
                || pendingPos == null
                || pendingLevel != level
                || !pendingPos.equals(pos)
                || result == null
                || (!result.is(Items.WATER_BUCKET)
                && !result.is(WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get()))) {
            return;
        }
        CanonicalWater.drainVolume(pendingLevel, pendingPos, WaterVolumeChunk.UNITS_PER_BLOCK);
    }

    private static boolean isCanonicalPickupWater(LiquidBlock liquidBlock) {
        return WaterCompatibility.isCanonicalWaterFluid(liquidBlock.fluid);
    }
}
