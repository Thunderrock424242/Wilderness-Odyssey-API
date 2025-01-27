package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireBlock.class)
public class CampfireBlockMixin {

    /**
     * Modify the smoke particle behavior to extend to the max build height.
     */
    @Inject(method = "makeParticles", at = @At("HEAD"), cancellable = true)
    private static void modifyMakeParticles(Level level, BlockPos pos, boolean isSignalFire, boolean spawnExtraSmoke, CallbackInfo ci) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 5; ++i) {
            // Calculate the start position for the smoke
            double x = pos.getX() + 0.5 + random.nextGaussian() * 0.1;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.1;

            // Use signal or cosy smoke based on the campfire type
            level.addAlwaysVisibleParticle(
                    isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    true,   // Force rendering even at far distances
                    x, y, z,
                    0.0, 0.1, 0.0 // Motion (upward)
            );
        }

        ci.cancel(); // Prevent the original particle logic
    }
}
