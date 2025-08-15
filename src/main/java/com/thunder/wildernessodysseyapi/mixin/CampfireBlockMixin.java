package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * The type Campfire block mixin.
 */
@Mixin(CampfireBlock.class)
public class CampfireBlockMixin {

    // Track last particle emission times keyed by block position to throttle
    @Unique
    private static final Long2LongOpenHashMap LAST_PARTICLE_TIME = new Long2LongOpenHashMap();

    @Inject(method = "makeParticles", at = @At("HEAD"), cancellable = true)
    private static void modifyMakeParticles(Level level, BlockPos pos, boolean isSignalFire, boolean spawnExtraSmoke, CallbackInfo ci) {
        if (!level.isClientSide) return;

        if (!level.canSeeSky(pos.above())) {
            return; // Let vanilla handle smoke when covered
        }

        long gameTime = level.getGameTime();
        long key = pos.asLong();
        long lastTime = LAST_PARTICLE_TIME.getOrDefault(key, 0L);

        // Throttle particle spawn per campfire every 5 ticks (1/4 second)
        if (gameTime - lastTime < 5) return;
        LAST_PARTICLE_TIME.put(key, gameTime);

        // Periodically clean up stale entries to avoid unbounded growth
        if (gameTime % 200 == 0) {
            LongIterator iter = LAST_PARTICLE_TIME.keySet().iterator();
            while (iter.hasNext()) {
                long k = iter.nextLong();
                long value = LAST_PARTICLE_TIME.get(k);
                if (gameTime - value > 200) {
                    iter.remove();
                }
            }
        }

        RandomSource random = level.getRandom();
        int maxHeight = level.getMaxBuildHeight();

        // Generate a sparse column effect
        for (int i = 0; i < 2; ++i) {
            double x = pos.getX() + 0.5 + random.nextGaussian() * 0.05;
            double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.05;

            int steps = 5 + random.nextInt(4); // Vary how many particles per column
            for (int j = 0; j < steps; j++) {
                int height = pos.getY() + 10 + random.nextInt(maxHeight - pos.getY() - 10);
                level.addAlwaysVisibleParticle(
                        isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        true,
                        x, height, z,
                        0.0, 0.07 + random.nextDouble() * 0.03, 0.0
                );
            }
        }

        ci.cancel(); // prevent vanilla behavior
    }
}