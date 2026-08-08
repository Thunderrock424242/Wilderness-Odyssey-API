package com.thunder.wildernessodysseyapi.riftfall;

import com.thunder.wildernessodysseyapi.riftfall.config.RiftfallConfig;
import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.entity.RiftbornEntity;
import com.thunder.wildernessodysseyapi.entity.RiftboundWraithEntity;
import com.thunder.wildernessodysseyapi.entity.RiftListenerEntity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
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
    private static final int EXPOSURE_EFFECT_REFRESH_INTERVAL_TICKS = 20;

    private static final Map<net.minecraft.resources.ResourceKey<Level>, RiftfallState> states = new HashMap<>();
    private static final Map<UUID, Float> playerExposure = new HashMap<>();

    private RiftfallSystem() {}

    /**
     * Returns an aggregate stage for legacy callers that do not own a level.
     * New gameplay code should use {@link #stage(ServerLevel)} so separate
     * dimensions cannot leak Riftfall state into one another.
     */
    @Deprecated(forRemoval = false)
    public static RiftfallStage stage() {
        return states.values().stream()
                .map(state -> state.stage)
                .filter(stage -> stage != RiftfallStage.CLEAR)
                .findFirst()
                .orElse(RiftfallStage.CLEAR);
    }

    /** Returns the Riftfall stage owned by one server dimension. */
    public static RiftfallStage stage(ServerLevel level) {
        if (!canRunIn(level)) {
            return RiftfallStage.CLEAR;
        }
        RiftfallState state = states.get(level.dimension());
        return state == null ? RiftfallStage.CLEAR : state.stage;
    }

    public static float getExposure(ServerPlayer player) {
        return playerExposure.getOrDefault(player.getUUID(), 0F);
    }

    public static boolean canRunIn(ServerLevel level) {
        return RiftfallDimensionRules.isEligible(level.dimension());
    }

    public static void tick(ServerLevel level) {
        if (!canRunIn(level)) {
            return;
        }

        if (!RiftfallConfig.CONFIG.enabled()) {
            resetToClear();
            return;
        }

        RiftfallState state = stateFor(level);

        if (state.cooldownTicksRemaining > 0) state.cooldownTicksRemaining--;

        LocalWeather localWeather = weatherAtPlayers(level);
        if (!localWeather.precipitating() && state.stage != RiftfallStage.CLEAR) {
            enterStage(level, RiftfallStage.ENDING, RiftfallConfig.CONFIG.endingTicks());
        }

        if (state.stage == RiftfallStage.CLEAR) {
            maybeStartRiftfall(level, localWeather);
        } else {
            state.stageTicksRemaining--;
            if (state.stageTicksRemaining <= 0) {
                advanceStage(level);
            }
        }

        RiftfallStage currentStage = stateFor(level).stage;
        tickExposure(level, currentStage);
        tickCorrosion(level, currentStage);
        tickRiftbornSpawning(level, currentStage);
        tickRiftListenerSpawning(level, currentStage);
        tickRiftboundWraithSpawning(level, currentStage);
    }

    private static void maybeStartRiftfall(ServerLevel level, LocalWeather localWeather) {
        RiftfallState state = stateFor(level);
        if (state.cooldownTicksRemaining > 0) return;
        if (!localWeather.precipitating()) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.checkIntervalTicks()) != 0) return;

        double chance = RiftfallConfig.CONFIG.baseStartChance();
        if (localWeather.thundering()) chance *= RiftfallConfig.CONFIG.thunderMultiplier();

        if (level.random.nextDouble() < chance) {
            enterStage(level, RiftfallStage.WARNING, RiftfallConfig.CONFIG.warningTicks());
            broadcast(level, "ATLAS WARNING: Atmospheric anomaly detected. Seek shelter.", ChatFormatting.LIGHT_PURPLE);
        }
    }

    /** Resolves dimension-wide Riftfall eligibility from player-local authority. */
    private static LocalWeather weatherAtPlayers(ServerLevel level) {
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            boolean wet = level.isRaining() || level.isThundering();
            return new LocalWeather(wet, level.isThundering());
        }

        WeatherQuery weather = WeatherServices.query();
        boolean precipitating = false;
        boolean thundering = false;
        for (ServerPlayer player : level.players()) {
            BlockPos position = player.blockPosition();
            precipitating |= weather.isPrecipitatingAt(level, position);
            thundering |= weather.isThunderingAt(level, position);
            if (precipitating && thundering) {
                break;
            }
        }
        return new LocalWeather(precipitating, thundering);
    }

    private record LocalWeather(boolean precipitating, boolean thundering) {
    }

    private static void advanceStage(ServerLevel level) {
        RiftfallState state = stateFor(level);
        switch (state.stage) {
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
                stateFor(level).cooldownTicksRemaining = RiftfallConfig.CONFIG.cooldownTicks();
                broadcast(level, "Storm intensity decreasing. Remain cautious.", ChatFormatting.GRAY);
            }
            case CLEAR -> {
            }
        }
    }

    private static void enterStage(ServerLevel level, RiftfallStage nextStage, int ticks) {
        RiftfallState state = stateFor(level);
        state.stage = nextStage;
        state.stageTicksRemaining = ticks;

        if (nextStage == RiftfallStage.METEOR_SURGE && (level.getGameTime() % 100 == 0)) {
            spawnMeteorFlavor(level);
        }
    }

    private static void tickExposure(ServerLevel level, RiftfallStage stage) {
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
            if (player.tickCount % EXPOSURE_EFFECT_REFRESH_INTERVAL_TICKS == 0) {
                applyExposureEffects(player, value);
            }
        }
    }

    private static boolean playerCanSeeSky(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return level.canSeeSky(pos) || level.canSeeSky(pos.above());
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
        states.clear();
        playerExposure.clear();
    }

    private static void tickCorrosion(ServerLevel level, RiftfallStage stage) {
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

    private static void tickRiftbornSpawning(ServerLevel level, RiftfallStage stage) {
        if (!stage.isActiveDanger()) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.riftbornSpawnIntervalTicks()) != 0) return;

        EntityType<RiftbornEntity> type = ModEntities.RIFTBORN.get();
        int globalCount = level.getEntities(type, new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000, 30_000_000, level.getMaxBuildHeight(), 30_000_000), RiftbornEntity::isAlive).size();
        if (globalCount >= RiftfallConfig.CONFIG.maxRiftbornGlobal()) return;

        int budget = stage == RiftfallStage.METEOR_SURGE
                ? RiftfallConfig.CONFIG.riftbornSpawnBudgetSurge()
                : RiftfallConfig.CONFIG.riftbornSpawnBudgetActive();
        if (budget <= 0) return;

        for (ServerPlayer player : level.players()) {
            if (budget <= 0) break;
            if (!playerCanSeeSky(level, player)) continue;
            int nearby = level.getEntities(type, new AABB(player.blockPosition()).inflate(32), RiftbornEntity::isAlive).size();
            if (nearby >= RiftfallConfig.CONFIG.maxRiftbornPerPlayer()) continue;

            BlockPos spawn = findGroundNear(level, player.blockPosition(), 18, 36);
            if (spawn == null) continue;

            RiftbornEntity mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
            if (mob.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.EVENT) && mob.checkSpawnObstruction(level)) {
                level.addFreshEntity(mob);
                budget--;
            }
        }
    }

    private static void tickRiftListenerSpawning(ServerLevel level, RiftfallStage stage) {
        if (!stage.isActiveDanger()) return;
        if (RiftfallConfig.CONFIG.maxRiftListenersGlobal() <= 0) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.riftListenerSpawnIntervalTicks()) != 0) return;

        double chance = RiftfallConfig.CONFIG.riftListenerSpawnChance();
        if (stage == RiftfallStage.METEOR_SURGE) {
            chance = Math.min(1.0D, chance * 1.5D);
        }
        if (level.random.nextDouble() > chance) return;

        EntityType<RiftListenerEntity> type = ModEntities.RIFT_LISTENER.get();
        int globalCount = level.getEntities(type, new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000, 30_000_000, level.getMaxBuildHeight(), 30_000_000), RiftListenerEntity::isAlive).size();
        if (globalCount >= RiftfallConfig.CONFIG.maxRiftListenersGlobal()) return;

        for (ServerPlayer player : level.players()) {
            if (!playerCanSeeSky(level, player)) continue;
            int nearby = level.getEntities(type, new AABB(player.blockPosition()).inflate(56), RiftListenerEntity::isAlive).size();
            if (nearby >= RiftfallConfig.CONFIG.maxRiftListenersPerPlayer()) continue;

            BlockPos spawn = findGroundNear(level, player.blockPosition(), 24, 44);
            if (spawn == null) continue;

            RiftListenerEntity listener = type.create(level);
            if (listener == null) continue;
            listener.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
            if (listener.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.EVENT) && listener.checkSpawnObstruction(level)) {
                level.addFreshEntity(listener);
                return;
            }
        }
    }

    private static void tickRiftboundWraithSpawning(ServerLevel level, RiftfallStage stage) {
        if (!stage.isActiveDanger()) return;
        if (RiftfallConfig.CONFIG.maxRiftboundWraithsGlobal() <= 0) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.riftboundWraithSpawnIntervalTicks()) != 0) return;

        double chance = RiftfallConfig.CONFIG.riftboundWraithSpawnChance();
        if (stage == RiftfallStage.METEOR_SURGE) {
            chance = Math.min(1.0D, chance * 1.6D);
        }
        if (level.random.nextDouble() > chance) return;

        EntityType<RiftboundWraithEntity> type = ModEntities.RIFTBOUND_WRAITH.get();
        int globalCount = level.getEntities(type, new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000, 30_000_000, level.getMaxBuildHeight(), 30_000_000), RiftboundWraithEntity::isAlive).size();
        if (globalCount >= RiftfallConfig.CONFIG.maxRiftboundWraithsGlobal()) return;

        for (ServerPlayer player : level.players()) {
            if (!playerCanSeeSky(level, player)) continue;
            int nearby = level.getEntities(type, new AABB(player.blockPosition()).inflate(64), RiftboundWraithEntity::isAlive).size();
            if (nearby >= RiftfallConfig.CONFIG.maxRiftboundWraithsPerPlayer()) continue;

            BlockPos spawn = findGroundNear(level, player.blockPosition(), 28, 52);
            if (spawn == null) continue;

            RiftboundWraithEntity wraith = type.create(level);
            if (wraith == null) continue;
            wraith.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
            if (wraith.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.EVENT) && wraith.checkSpawnObstruction(level)) {
                level.addFreshEntity(wraith);
                return;
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

    private static RiftfallState stateFor(ServerLevel level) {
        return states.computeIfAbsent(level.dimension(), ignored -> new RiftfallState());
    }

    private static final class RiftfallState {
        private RiftfallStage stage = RiftfallStage.CLEAR;
        private int stageTicksRemaining = 0;
        private int cooldownTicksRemaining = 0;
    }
}
