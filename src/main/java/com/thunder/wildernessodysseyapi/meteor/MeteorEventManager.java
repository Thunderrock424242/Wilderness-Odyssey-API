package com.thunder.wildernessodysseyapi.meteor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Handles server-side meteor strike effects (explosion, fire, and debris).
 */
public final class MeteorEventManager {

    private MeteorEventManager() {
    }

    public static void triggerMeteorStrike(ServerLevel level, BlockPos impactCenter, float size) {
        float clampedSize = Mth.clamp(size, 1.0F, 8.0F);
        RandomSource random = level.getRandom();

        level.playSound(
                null,
                impactCenter,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.WEATHER,
                4.0F,
                0.65F + random.nextFloat() * 0.2F
        );

        float explosionPower = 2.0F + (clampedSize * 1.25F);
        level.explode(
                null,
                impactCenter.getX() + 0.5D,
                impactCenter.getY() + 0.5D,
                impactCenter.getZ() + 0.5D,
                explosionPower,
                true,
                Explosion.BlockInteraction.DESTROY_WITH_DECAY
        );

        spawnFire(level, impactCenter, clampedSize, random);
        spawnDebris(level, impactCenter, clampedSize, random);
        spawnImpactParticles(level, impactCenter, clampedSize);
    }

    private static void spawnFire(ServerLevel level, BlockPos center, float size, RandomSource random) {
        int radius = Math.max(2, Mth.floor(size * 1.8F));
        int attempts = 30 + Mth.floor(size * 12.0F);

        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos floor = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset(dx, 0, dz));
            BlockPos placePos = floor.above();
            if (level.isEmptyBlock(placePos) && Blocks.FIRE.defaultBlockState().canSurvive(level, placePos)) {
                level.setBlock(placePos, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    private static void spawnDebris(ServerLevel level, BlockPos center, float size, RandomSource random) {
        int debrisCount = 10 + Mth.floor(size * 6.0F);
        float spread = 1.5F + size;

        for (int i = 0; i < debrisCount; i++) {
            double x = center.getX() + 0.5D + (random.nextDouble() - 0.5D) * spread;
            double y = center.getY() + 0.5D + random.nextDouble() * 1.6D;
            double z = center.getZ() + 0.5D + (random.nextDouble() - 0.5D) * spread;

            BlockState debrisState = random.nextFloat() < 0.7F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
            FallingBlockEntity debris = FallingBlockEntity.fall(level, BlockPos.containing(x, y, z), debrisState);
            debris.setHurtsEntities(size, 20);
            debris.setDeltaMovement(
                    (random.nextDouble() - 0.5D) * 0.7D,
                    0.35D + random.nextDouble() * 0.5D,
                    (random.nextDouble() - 0.5D) * 0.7D
            );
        }
    }

    private static void spawnImpactParticles(ServerLevel level, BlockPos center, float size) {
        int burst = 120 + Mth.floor(size * 40.0F);
        level.sendParticles(ParticleTypes.FLAME,
                center.getX() + 0.5D,
                center.getY() + 1.0D,
                center.getZ() + 0.5D,
                burst,
                size * 0.6D,
                size * 0.25D,
                size * 0.6D,
                0.02D);

        BlockState smokeState = Blocks.BLACKSTONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, smokeState),
                center.getX() + 0.5D,
                center.getY() + 0.8D,
                center.getZ() + 0.5D,
                burst,
                size * 0.7D,
                size * 0.3D,
                size * 0.7D,
                0.02D);

        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.getX() + 0.5D,
                center.getY() + 0.8D,
                center.getZ() + 0.5D,
                40 + Mth.floor(size * 12.0F),
                size * 0.4D,
                size * 0.2D,
                size * 0.4D,
                0.01D);
    }
}
