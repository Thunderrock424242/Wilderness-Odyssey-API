package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The type Campfire block mixin.
 */
@Mixin(CampfireBlock.class)
public class CampfireBlockMixin {

    /**
     * Modify the smoke particles to rise to the maximum build height and extend visibility.
     */
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void onAnimateTick(
            BlockState state,
            Level level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        // cancel vanilla smoke code
        ci.cancel();

        // read “signal fire?” from the block state
        boolean isSignalFire = state.getValue(CampfireBlock.SIGNAL_FIRE);
        // if you want to mimic vanilla “extra smoke” randomness:
        boolean extraSmoke = random.nextInt(5) == 0;

        int maxY = level.getMaxBuildHeight();

        // 1) a little “dense puff” around the campfire
        for (int i = 0; i < 5; i++) {
            double x = pos.getX() + 0.5  + random.nextGaussian() * 0.1;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5  + random.nextGaussian() * 0.1;
            level.addAlwaysVisibleParticle(
                    isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z,
                    0.0, 0.1, 0.0
            );
        }

        // 2) a continuous column up to world build height
        for (int yy = pos.getY() + 1; yy < maxY; yy++) {
            double x = pos.getX() + 0.5;
            double z = pos.getZ() + 0.5;
            level.addAlwaysVisibleParticle(
                    isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, yy, z,
                    0.0, 0.1, 0.0
            );
        }
    }
}

// this corresponds to the Higher smoke package.