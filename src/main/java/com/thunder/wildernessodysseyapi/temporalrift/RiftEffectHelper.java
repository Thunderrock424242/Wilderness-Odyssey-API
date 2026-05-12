package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class RiftEffectHelper {
    private static final double PULL_RADIUS = 8.0D;
    private static final double EARTHQUAKE_RADIUS = 192.0D;

    private RiftEffectHelper() {
    }

    public static void playOpeningEffects(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 130, 2.2D, 1.4D, 2.2D, 0.18D);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D, 55, 1.2D, 0.4D, 1.2D, 0.03D);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2.8F, 0.65F);
        level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.1F, 0.45F);
        triggerEarthquake(level, pos);
        if (TemporalRiftConfig.ENABLE_RIFT_OPENING_DISASTER.get()) {
            triggerOpeningDisaster(level, pos);
        }
    }

    public static void playClosingEffects(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 90, 1.2D, 1.0D, 1.2D, 0.32D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D, 35, 0.9D, 0.35D, 0.9D, 0.05D);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 2.0F, 0.55F);
    }

    public static void playTransitEarthquake(ServerLevel level, BlockPos pos) {
        triggerEarthquake(level, pos);
    }

    public static void tickOpenRift(ServerLevel level, BlockPos pos) {
        long gameTime = level.getGameTime();
        if (gameTime % 5L == 0L) {
            pulseParticles(level, pos);
        }
        if (TemporalRiftConfig.ENABLE_RIFT_PULL_EFFECT.get()) {
            pullNearbyPlayers(level, pos);
        }
        if (TemporalRiftConfig.ENABLE_RIFT_TERRAIN_AGING.get() && gameTime % 40L == 0L) {
            ageNearbyBlock(level, pos);
        }
        if (gameTime % 80L == 0L) {
            level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.7F, 0.55F + level.getRandom().nextFloat() * 0.2F);
        }
    }

    private static void pulseParticles(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 20, 0.7D, 1.1D, 0.7D, 0.14D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.15D, pos.getZ() + 0.5D, 8, 1.5D, 0.08D, 1.5D, 0.03D);
        if (random.nextInt(5) == 0) {
            level.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 4, 0.4D, 0.6D, 0.4D, 0.02D);
        }
    }

    private static void pullNearbyPlayers(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        List<Player> nearby = level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(PULL_RADIUS));
        for (Player player : nearby) {
            if (player.isSpectator()) {
                continue;
            }

            Vec3 offset = center.subtract(player.position());
            double distance = Math.max(0.35D, offset.length());
            double strength = Math.max(0.0D, 1.0D - distance / PULL_RADIUS) * 0.045D;
            Vec3 pull = offset.normalize().scale(strength);
            player.push(pull.x, Math.max(-0.01D, pull.y * 0.35D), pull.z);
        }
    }

    private static void triggerEarthquake(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        RandomSource random = level.getRandom();

        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.8D, center.z, 80, 3.5D, 1.0D, 3.5D, 0.05D);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 12.0F, 0.55F);

        for (Player player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }

            double distance = Math.sqrt(player.distanceToSqr(center));
            if (distance > EARTHQUAKE_RADIUS) {
                continue;
            }

            double strength = Math.max(0.05D, 1.0D - distance / EARTHQUAKE_RADIUS);
            double horizontalX = (random.nextDouble() - 0.5D) * 0.28D * strength;
            double horizontalZ = (random.nextDouble() - 0.5D) * 0.28D * strength;
            double vertical = 0.05D + 0.18D * strength;
            player.push(horizontalX, vertical, horizontalZ);
        }
    }

    private static void triggerOpeningDisaster(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        RandomSource random = level.getRandom();

        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 1.0D, center.z, 160, 12.0D, 2.0D, 12.0D, 0.02D);
        level.sendParticles(ParticleTypes.ASH, center.x, center.y + 2.0D, center.z, 110, 14.0D, 3.0D, 14.0D, 0.01D);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.5D, center.z, 90, 9.0D, 1.0D, 9.0D, 0.03D);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 16.0F, 0.42F);
        level.playSound(null, pos, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.60F);

        scareNearbyAnimals(level, center);
        breakFragileRim(level, pos, random);
    }

    private static void scareNearbyAnimals(ServerLevel level, Vec3 center) {
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, new AABB(center, center).inflate(42.0D));
        for (Animal animal : animals) {
            Vec3 direction = animal.position().subtract(center);
            if (direction.lengthSqr() < 0.0001D) {
                continue;
            }

            Vec3 flee = direction.normalize().scale(0.42D);
            animal.push(flee.x, 0.12D, flee.z);
        }
    }

    private static void breakFragileRim(ServerLevel level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 28; i++) {
            int dx = random.nextInt(29) - 14;
            int dz = random.nextInt(29) - 14;
            BlockPos sample = pos.offset(dx, random.nextInt(5) - 2, dz);
            BlockState state = level.getBlockState(sample);
            BlockState replacement = rimShockReplacement(state);
            if (replacement != null) {
                level.setBlock(sample, replacement, 3);
            }
        }
    }

    private static BlockState rimShockReplacement(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT)) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (state.is(Blocks.STONE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (state.is(Blocks.SAND)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        return null;
    }

    private static void ageNearbyBlock(ServerLevel level, BlockPos riftPos) {
        RandomSource random = level.getRandom();
        BlockPos pos = riftPos.offset(random.nextInt(15) - 7, random.nextInt(5) - 2, random.nextInt(15) - 7);
        BlockState state = level.getBlockState(pos);
        BlockState replacement = agedState(state);
        if (replacement != null && !state.is(replacement.getBlock())) {
            level.setBlock(pos, replacement, 3);
        }
    }

    private static BlockState agedState(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT)) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (state.is(Blocks.STONE)) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (state.is(Blocks.COBBLESTONE)) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (state.is(Blocks.SAND)) {
            return Blocks.SOUL_SAND.defaultBlockState();
        }
        return null;
    }
}
