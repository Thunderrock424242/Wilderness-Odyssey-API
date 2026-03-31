package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional hard-replacement hook for vanilla water updates.
 */
@Mixin(net.minecraft.world.level.block.LiquidBlock.class)
public class LiquidBlockVolumetricMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildernessodysseyapi$replaceVanillaWaterTick(BlockState state,
                                                              ServerLevel level,
                                                              BlockPos pos,
                                                              RandomSource random,
                                                              CallbackInfo ci) {
        if (state.getFluidState().getType() == Fluids.WATER) {
            if (!VolumetricFluidManager.shouldReplaceVanillaWaterEngine()) {
                return;
            }
            VolumetricFluidManager.ingestVanillaWaterTick(level, pos, state.getFluidState().getAmount() / 8.0D);
            ci.cancel();
            return;
        }
        if (state.getFluidState().getType() == Fluids.LAVA && VolumetricFluidManager.shouldReplaceVanillaLavaEngine()) {
            VolumetricFluidManager.ingestVanillaLavaTick(level, pos, state.getFluidState().getAmount() / 8.0D);
            ci.cancel();
        }
    }
}
