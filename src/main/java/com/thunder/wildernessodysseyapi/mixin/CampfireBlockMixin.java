package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireBlock.class)
public class CampfireBlockMixin {

    /**
     * Modify the smoke particles to rise to the maximum build height and extend visibility.
     */
    @Inject(method = "makeParticles", at = @At("HEAD"), cancellable = true)
    private static void modifyMakeParticles(Level level, BlockPos pos, boolean isSignalFire, boolean spawnExtraSmoke, CallbackInfo ci) {
        RandomSource random = level.getRandom();
        int maxBuildHeight = level.getMaxBuildHeight();

        // Spawn multiple particles to simulate a dense smoke column
        for (int i = 0; i < 5; ++i) {
            double x = pos.getX() + 0.5 + random.nextGaussian() * 0.1;
            double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.1;
            double y = pos.getY() + 0.5;

            // Spawn particles with adjusted height and motion
            for (int currentHeight = (int) y; currentHeight < maxBuildHeight; currentHeight++) {
                level.addAlwaysVisibleParticle(
                        isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        true,
                        x, currentHeight, z,
                        0.0, 0.1, 0.0 // Natural upward motion
                );
            }
        }

        // Cancel vanilla particle spawning
        ci.cancel();
    }
}

// this corresponds to the Higher smoke package.