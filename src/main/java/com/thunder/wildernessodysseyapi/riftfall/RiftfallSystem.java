package com.thunder.wildernessodysseyapi.riftfall;

import com.thunder.wildernessodysseyapi.config.RiftfallConfig;
import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.entity.PurpleStormMonsterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RiftfallSystem {
    private static RiftfallStage stage = RiftfallStage.CLEAR;
    private static int stageTicksRemaining = 0;
    private static int cooldownTicksRemaining = 0;

    private static final Map<UUID, Float> playerExposure = new HashMap<>();

    private RiftfallSystem() {}

    public static RiftfallStage stage() {
        return stage;
    }

    public static float getExposure(ServerPlayer player) {
        return playerExposure.getOrDefault(player.getUUID(), 0F);
    }

    public static void tick(ServerLevel level) {
        if (!RiftfallConfig.CONFIG.enabled()) {
            resetToClear();
            return;
        }

        if (cooldownTicksRemaining > 0) cooldownTicksRemaining--;

        boolean vanillaWet = level.isRaining() || level.isThundering();
        if (!vanillaWet && stage != RiftfallStage.CLEAR) {
            enterStage(level, RiftfallStage.ENDING, RiftfallConfig.CONFIG.endingTicks());
        }

        if (stage == RiftfallStage.CLEAR) {
            maybeStartRiftfall(level);
        } else {
            stageTicksRemaining--;
            if (stageTicksRemaining <= 0) {
                advanceStage(level);
            }
        }

        tickExposure(level);
        tickCorrosion(level);
        tickRiftbornSpawning(level);
    }

    private static void maybeStartRiftfall(ServerLevel level) {
        if (cooldownTicksRemaining > 0) return;
        if (!level.isRaining() && !level.isThundering()) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.checkIntervalTicks()) != 0) return;

        double chance = RiftfallConfig.CONFIG.baseStartChance();
        if (level.isThundering()) chance *= RiftfallConfig.CONFIG.thunderMultiplier();

        if (level.random.nextDouble() < chance) {
            enterStage(level, RiftfallStage.WARNING, RiftfallConfig.CONFIG.warningTicks());
            broadcast(level, "ATLAS WARNING: Atmospheric anomaly detected. Seek shelter.", ChatFormatting.LIGHT_PURPLE);
        }
    }

    private static void advanceStage(ServerLevel level) {
        switch (stage) {
            case WARNING -> {
                enterStage(level, RiftfallStage.ACTIVE, RiftfallConfig.CONFIG.activeTicks());
                broadcast(level, "Riftfall formation detected. Chrono Corrosion levels rising.", ChatFormatting.DARK_PURPLE);
            }
            case ACTIVE -> {
                if (level.random.nextDouble() < RiftfallConfig.CONFIG.meteorSurgeChance()) {
                    enterStage(level, RiftfallStage.METEOR_SURGE, RiftfallConfig.CONFIG.meteorSurgeTicks());
                    broadcast(level, "Meteor activity increasing. Shelter recommended.", ChatFormatting.RED);
                } else {
                    enterStage(level, RiftfallStage.ENDING, RiftfallConfig.CONFIG.endingTicks());
                }
            }
            case METEOR_SURGE -> enterStage(level, RiftfallStage.ENDING, RiftfallConfig.CONFIG.endingTicks());
            case ENDING -> {
                enterStage(level, RiftfallStage.CLEAR, 0);
                cooldownTicksRemaining = RiftfallConfig.CONFIG.cooldownTicks();
                broadcast(level, "Storm intensity decreasing. Remain cautious.", ChatFormatting.GRAY);
            }
            case CLEAR -> {
            }
        }
    }

    private static void enterStage(ServerLevel level, RiftfallStage nextStage, int ticks) {
        stage = nextStage;
        stageTicksRemaining = ticks;

        if (nextStage == RiftfallStage.METEOR_SURGE && (level.getGameTime() % 100 == 0)) {
            spawnMeteorFlavor(level);
        }
    }

    private static void tickExposure(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            float value = getExposure(player);
            boolean exposed = playerCanSeeSky(level, player) && stage.isActiveDanger();
            if (exposed) {
                value += (float) RiftfallConfig.CONFIG.exposureGainPerTick();
            } else if (stage == RiftfallStage.CLEAR) {
                value -= (float) RiftfallConfig.CONFIG.exposureDecayClearPerTick();
            } else {
                value -= (float) RiftfallConfig.CONFIG.exposureDecayShelteredPerTick();
            }

            value = Mth.clamp(value, 0F, 100F);
            playerExposure.put(player.getUUID(), value);
            applyExposureEffects(player, value);
        }
    }

    private static boolean playerCanSeeSky(ServerLevel level, ServerPlayer player) {
        return level.canSeeSky(player.blockPosition()) || level.canSeeSky(player.blockPosition().above());
    }

    private static void applyExposureEffects(ServerPlayer player, float exposure) {
        if (exposure >= 35) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false, true));
        }
        if (exposure >= 70) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 0, true, false, true));
        }
    }

    private static void spawnMeteorFlavor(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            level.levelEvent(3000, player.blockPosition(), 0);
        }
    }

    private static void broadcast(ServerLevel level, String text, ChatFormatting style) {
        Component message = Component.literal(text).withStyle(style);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    private static void resetToClear() {
        stage = RiftfallStage.CLEAR;
        stageTicksRemaining = 0;
        cooldownTicksRemaining = 0;
        playerExposure.clear();
    }

    private static void tickCorrosion(ServerLevel level) {
        if (!stage.isActiveDanger() || !RiftfallConfig.CONFIG.allowNaturalBlockCorrosion()) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.corrosionIntervalTicks()) != 0) return;

        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < RiftfallConfig.CONFIG.corrosionChecksPerPlayerInterval(); i++) {
                BlockPos sample = player.blockPosition().offset(
                        level.random.nextInt(25) - 12,
                        level.random.nextInt(7) - 3,
                        level.random.nextInt(25) - 12
                );
                if (!level.canSeeSky(sample)) continue;
                BlockState state = level.getBlockState(sample);
                BlockState replacement = corrosionReplacement(state, level);
                if (replacement != null) {
                    level.setBlock(sample, replacement, 3);
                }
            }
        }
    }

    private static BlockState corrosionReplacement(BlockState state, ServerLevel level) {
        if (state.is(Blocks.GRASS_BLOCK) && level.random.nextFloat() < 0.12F) return Blocks.COARSE_DIRT.defaultBlockState();
        if (state.is(Blocks.DIRT) && level.random.nextFloat() < 0.10F) return Blocks.COARSE_DIRT.defaultBlockState();
        if (state.is(Blocks.STONE) && level.random.nextFloat() < 0.08F) return Blocks.COBBLESTONE.defaultBlockState();
        if (state.is(Blocks.COBBLESTONE) && level.random.nextFloat() < 0.08F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (state.is(Blocks.OAK_LEAVES) && level.random.nextFloat() < 0.08F) return Blocks.DEAD_BUSH.defaultBlockState();
        if (RiftfallConfig.CONFIG.allowCropDamage() && state.getBlock() instanceof CropBlock && level.random.nextFloat() < 0.05F) {
            return Blocks.DEAD_BUSH.defaultBlockState();
        }
        return null;
    }

    private static void tickRiftbornSpawning(ServerLevel level) {
        if (!stage.isActiveDanger()) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.riftbornSpawnIntervalTicks()) != 0) return;

        EntityType<PurpleStormMonsterEntity> type = ModEntities.PURPLE_STORM_MONSTER.get();
        int globalCount = level.getEntities(type, new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000, 30_000_000, level.getMaxBuildHeight(), 30_000_000)).size();
        if (globalCount >= RiftfallConfig.CONFIG.maxRiftbornGlobal()) return;

        int budget = stage == RiftfallStage.METEOR_SURGE
                ? RiftfallConfig.CONFIG.riftbornSpawnBudgetSurge()
                : RiftfallConfig.CONFIG.riftbornSpawnBudgetActive();
        if (budget <= 0) return;

        for (ServerPlayer player : level.players()) {
            if (budget <= 0) break;
            if (!playerCanSeeSky(level, player)) continue;
            int nearby = level.getEntities(type, new AABB(player.blockPosition()).inflate(32)).size();
            if (nearby >= RiftfallConfig.CONFIG.maxRiftbornPerPlayer()) continue;

            BlockPos spawn = findGroundNear(level, player.blockPosition(), 18, 36);
            if (spawn == null) continue;

            PurpleStormMonsterEntity mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
            if (mob.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.EVENT) && mob.checkSpawnObstruction(level)) {
                level.addFreshEntity(mob);
                budget--;
            }
        }
    }

    private static BlockPos findGroundNear(ServerLevel level, BlockPos origin, int minRadius, int maxRadius) {
        for (int i = 0; i < 8; i++) {
            int radius = minRadius + level.random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            double angle = level.random.nextDouble() * Math.PI * 2;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.canSeeSky(pos) && level.getBlockState(pos.below()).isSolid() && level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }
}
