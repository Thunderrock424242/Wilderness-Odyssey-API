package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents vanilla water ticks from creating a second, competing flow state.
 *
 * <p>NeoForge does not expose an event before {@link FlowingFluid} mutates
 * neighboring blocks, so this narrow mixin redirects only vanilla water to the
 * canonical finite-volume ticker. Lava and third-party fluids remain untouched.</p>
 */
@Mixin(FlowingFluid.class)
public abstract class CanonicalWaterFlowMixin {

    /** Imports legacy water once and lets the canonical active queue own later flow. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wildernessOdysseyApi$replaceVanillaWaterTick(
            Level level,
            BlockPos pos,
            FluidState state,
            CallbackInfo callbackInfo
    ) {
        if (!state.is(Fluids.WATER) && !state.is(Fluids.FLOWING_WATER)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            CanonicalWater.getOrImport(serverLevel, pos);
        }
        callbackInfo.cancel();
    }
}
