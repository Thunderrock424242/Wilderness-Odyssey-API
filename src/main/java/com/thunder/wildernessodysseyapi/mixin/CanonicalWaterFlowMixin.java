package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents vanilla water ticks from creating a second, competing flow state.
 *
 * <p>NeoForge does not expose an event before {@link FlowingFluid} mutates
 * neighboring blocks, so this narrow mixin redirects tracked cells and hands
 * disturbed, untracked Wilderness projections to finite authority before spread.
 * Untracked vanilla water keeps
 * normal gamerule behavior, preserving mods that intentionally use its flow.</p>
 */
@Mixin(FlowingFluid.class)
public abstract class CanonicalWaterFlowMixin {

    /** Cancels vanilla propagation only after the replacement system owns this cell. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wildernessOdysseyApi$replaceVanillaWaterTick(
            Level level,
            BlockPos pos,
            FluidState state,
            CallbackInfo callbackInfo
    ) {
        if (!WaterCompatibility.isTaggedWater(state)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel && WildernessWaterRules.isEnabled(serverLevel)) {
            if (!CanonicalWater.isTracked(serverLevel, pos)
                    && serverLevel.getBlockState(pos).is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get())) {
                CanonicalWater.getOrImport(serverLevel, pos);
                CanonicalWater.schedule(serverLevel, pos);
            }
            if (CanonicalWater.isTracked(serverLevel, pos)) {
                callbackInfo.cancel();
            }
        }
    }
}
