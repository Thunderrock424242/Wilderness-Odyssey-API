package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

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
        long lastTime = LAST_PARTICLE_TIME.get(key);

        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 256, false);
        if (player == null) {
            return;
        }

        double distanceSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distanceSq > 256 * 256) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameRenderer != null) {
            Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
            Vec3 cameraLook = minecraft.gameRenderer.getMainCamera().getLookVector();
            Vec3 toCampfire = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).subtract(cameraPos);
            if (distanceSq > 64 * 64 && cameraLook.dot(toCampfire.normalize()) < 0.2) {
                return;
            }
        }

        int throttleInterval = 5;
        int columns = 2;
        int minSteps = 5;
        int extraSteps = 4;
        if (distanceSq > 128 * 128) {
            throttleInterval = 20;
            columns = 1;
            minSteps = 1;
            extraSteps = 1;
        } else if (distanceSq > 64 * 64) {
            throttleInterval = 10;
            columns = 1;
            minSteps = 2;
            extraSteps = 2;
        }

        // Throttle particle spawn per campfire based on distance
        if (gameTime - lastTime < throttleInterval) return;
        LAST_PARTICLE_TIME.put(key, gameTime);

        // Periodically clean up stale entries to avoid unbounded growth
        if (gameTime % 200 == 0) {
            Long2LongMap.FastEntrySet entries = LAST_PARTICLE_TIME.long2LongEntrySet();
            var iterator = entries.fastIterator();
            while (iterator.hasNext()) {
                Long2LongMap.Entry entry = iterator.next();
                if (gameTime - entry.getLongValue() > 200) {
                    iterator.remove();
                }
            }
        }

        RandomSource random = level.getRandom();
        int maxHeight = level.getMaxBuildHeight();
        int minHeight = pos.getY() + 10;
        int heightRange = maxHeight - minHeight;
        if (heightRange <= 0) {
            return;
        }

        // Generate a sparse column effect
        for (int i = 0; i < columns; ++i) {
            double x = pos.getX() + 0.5 + random.nextGaussian() * 0.05;
            double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.05;

            int steps = minSteps + random.nextInt(extraSteps); // Vary how many particles per column
            for (int j = 0; j < steps; j++) {
                int height = minHeight + random.nextInt(heightRange);
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
