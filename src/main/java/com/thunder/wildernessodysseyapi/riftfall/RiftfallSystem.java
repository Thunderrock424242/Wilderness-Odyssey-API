package com.thunder.wildernessodysseyapi.riftfall;

import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceService;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceType;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import com.thunder.wildernessodysseyapi.meteor.event.MeteorImpactEvent;
import com.thunder.wildernessodysseyapi.riftfall.config.RiftfallConfig;
import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.entity.RiftbornEntity;
import com.thunder.wildernessodysseyapi.entity.RiftboundWraithEntity;
import com.thunder.wildernessodysseyapi.entity.RiftListenerEntity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.vegetation.api.PlantDisturbanceType;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Owns server-side Riftfall phase, exposure, corrosion, and event spawning.
 *
 * <p>Mutable state is partitioned by logical server so an integrated client can
 * leave one world and open another without inheriting the previous world's
 * phases or player exposure. {@link RiftfallLifecycleEvents} releases entries
 * promptly when players, levels, and servers leave their owning lifecycle.</p>
 */
public final class RiftfallSystem {
    private static final int EXPOSURE_EFFECT_REFRESH_INTERVAL_TICKS = 20;

    // Weak server keys provide a final safety net if an abnormal shutdown skips
    // NeoForge lifecycle events. Values never retain their owning server.
    private static final Map<MinecraftServer, ServerRiftfallState> SERVER_STATES = new WeakHashMap<>();

    private RiftfallSystem() {}

    /**
     * Returns an aggregate stage for legacy callers that do not own a level.
     * New gameplay code should use {@link #stage(ServerLevel)} so separate
     * dimensions cannot leak Riftfall state into one another.
     */
    @Deprecated(forRemoval = false)
    public static RiftfallStage stage() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return RiftfallStage.CLEAR;
        }
        return serverState(server).dimensions.values().stream()
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
        RiftfallState state = serverState(level.getServer()).dimensions.get(level.dimension());
        return state == null ? RiftfallStage.CLEAR : state.stage;
    }

    /** Returns the current server-owned exposure value for a player. */
    public static float getExposure(ServerPlayer player) {
        return serverState(player.getServer()).playerExposure.getOrDefault(player.getUUID(), 0F);
    }

    public static boolean canRunIn(ServerLevel level) {
        return RiftfallDimensionRules.isEligible(level.dimension());
    }

    public static void tick(ServerLevel level) {
        advanceClock(level);
        tickOptionalGameplay(level);
    }

    /**
     * Advances authoritative phase, cooldown, and exposure state every server tick.
     *
     * <p>This path remains bounded by the connected player count and never performs block sampling
     * or entity search. It must run even when Minecraft reports no spare optional-work time.</p>
     */
    public static void advanceClock(ServerLevel level) {
        if (!canRunIn(level)) {
            return;
        }

        if (!RiftfallConfig.CONFIG.enabled()) {
            resetGameplayState(level.getServer());
            return;
        }

        RiftfallState state = stateFor(level);

        if (state.cooldownTicksRemaining > 0) state.cooldownTicksRemaining--;
        if (state.stage != RiftfallStage.CLEAR) {
            state.stageTicksRemaining--;
            if (state.stageTicksRemaining <= 0) {
                advanceStage(level);
            }
        }

        RiftfallStage currentStage = stateFor(level).stage;
        tickExposure(level, currentStage);
    }

    /**
     * Runs weather sampling, terrain effects, and spawn searches only while spare tick time exists.
     */
    public static void tickOptionalGameplay(ServerLevel level) {
        if (!canRunIn(level) || !RiftfallConfig.CONFIG.enabled()) {
            return;
        }
        RiftfallState state = stateFor(level);
        LocalWeather localWeather = weatherAtPlayers(level);
        if (!localWeather.precipitating() && state.stage != RiftfallStage.CLEAR
                && state.stage != RiftfallStage.ENDING) {
            enterStage(level, RiftfallStage.ENDING, RiftfallConfig.CONFIG.endingTicks());
        } else if (state.stage == RiftfallStage.CLEAR) {
            maybeStartRiftfall(level, localWeather);
        }

        RiftfallStage currentStage = stateFor(level).stage;
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

        if (nextStage == RiftfallStage.ACTIVE || nextStage == RiftfallStage.METEOR_SURGE) {
            for (ServerPlayer player : level.players()) {
                WorldDisturbanceService.publish(
                        level,
                        player.blockPosition(),
                        WorldDisturbanceType.RIFTFALL,
                        nextStage == RiftfallStage.METEOR_SURGE ? 0.95 : 0.72,
                        96,
                        null,
                        false
                );
            }
        }

        if (nextStage == RiftfallStage.METEOR_SURGE) {
            int spawned = RiftfallConfig.CONFIG.realMeteorSurges()
                    ? MeteorImpactEvent.requestMeteorShower(
                            level,
                            RiftfallConfig.CONFIG.meteorSurgeMeteorCount(),
                            MeteorSiteSource.RIFTFALL
                    )
                    : 0;
            if (spawned == 0) {
                spawnMeteorFlavor(level);
            }
        }
    }

    private static void tickExposure(ServerLevel level, RiftfallStage stage) {
        Map<UUID, Float> playerExposure = serverState(level.getServer()).playerExposure;
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

    /** Releases all state owned by one server after shutdown or feature disable. */
    public static void clearServer(MinecraftServer server) {
        SERVER_STATES.remove(server);
    }

    private static void resetGameplayState(MinecraftServer server) {
        ServerRiftfallState state = SERVER_STATES.get(server);
        if (state != null) {
            state.dimensions.clear();
            state.playerExposure.clear();
        }
    }

    /** Releases phase state owned by an unloading dimension. */
    public static void clearLevel(ServerLevel level) {
        ServerRiftfallState state = SERVER_STATES.get(level.getServer());
        if (state != null) {
            state.dimensions.remove(level.dimension());
            state.entityCounts.remove(level.dimension());
        }
    }

    /** Releases non-persistent exposure when a player leaves the server. */
    public static void clearPlayer(ServerPlayer player) {
        ServerRiftfallState state = SERVER_STATES.get(player.getServer());
        if (state != null) {
            state.playerExposure.remove(player.getUUID());
        }
    }

    /** Records one tracked Riftfall entity after it joins a loaded server level. */
    static void onEntityAdded(ServerLevel level, EntityType<?> type) {
        if (type == ModEntities.RIFTBORN.get()) {
            entityCounts(level).riftborn++;
        } else if (type == ModEntities.RIFT_LISTENER.get()) {
            entityCounts(level).riftListeners++;
        } else if (type == ModEntities.RIFTBOUND_WRAITH.get()) {
            entityCounts(level).riftboundWraiths++;
        }
    }

    /** Records one tracked Riftfall entity after it leaves a loaded server level. */
    static void onEntityRemoved(ServerLevel level, EntityType<?> type) {
        RiftfallEntityCounts counts = existingEntityCounts(level);
        if (counts == null) {
            return;
        }
        if (type == ModEntities.RIFTBORN.get()) {
            counts.riftborn = Math.max(0, counts.riftborn - 1);
        } else if (type == ModEntities.RIFT_LISTENER.get()) {
            counts.riftListeners = Math.max(0, counts.riftListeners - 1);
        } else if (type == ModEntities.RIFTBOUND_WRAITH.get()) {
            counts.riftboundWraiths = Math.max(0, counts.riftboundWraiths - 1);
        }
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
                boolean crop = state.getBlock() instanceof CropBlock;
                boolean plantDamageAllowed = crop
                        ? RiftfallConfig.CONFIG.allowCropDamage() && level.random.nextFloat() < 0.05F
                        : level.random.nextFloat() < 0.08F;
                ReactiveVegetationServices.PlantDisturbanceResult plantResult =
                        ReactiveVegetationServices.applyDisturbanceAt(
                                level,
                                sample,
                                PlantDisturbanceType.RIFTFALL,
                                stage == RiftfallStage.METEOR_SURGE ? 1.0 : 0.78,
                                plantDamageAllowed
                        );
                if (plantResult.plant()) {
                    continue;
                }
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
        return null;
    }

    private static void tickRiftbornSpawning(ServerLevel level, RiftfallStage stage) {
        if (!stage.isActiveDanger()) return;
        if ((level.getGameTime() % RiftfallConfig.CONFIG.riftbornSpawnIntervalTicks()) != 0) return;

        EntityType<RiftbornEntity> type = ModEntities.RIFTBORN.get();
        int globalCap = RiftfallConfig.CONFIG.maxRiftbornGlobal();
        if (entityCounts(level).riftborn >= globalCap) return;

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
        int globalCap = RiftfallConfig.CONFIG.maxRiftListenersGlobal();
        if (entityCounts(level).riftListeners >= globalCap) return;

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
        int globalCap = RiftfallConfig.CONFIG.maxRiftboundWraithsGlobal();
        if (entityCounts(level).riftboundWraiths >= globalCap) return;

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
            BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.canSeeSky(pos) && level.getBlockState(pos.below()).isSolid() && level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static RiftfallState stateFor(ServerLevel level) {
        return serverState(level.getServer()).dimensions.computeIfAbsent(
                level.dimension(),
                ignored -> new RiftfallState()
        );
    }

    private static RiftfallEntityCounts entityCounts(ServerLevel level) {
        return serverState(level.getServer()).entityCounts.computeIfAbsent(
                level.dimension(),
                ignored -> new RiftfallEntityCounts()
        );
    }

    private static RiftfallEntityCounts existingEntityCounts(ServerLevel level) {
        ServerRiftfallState state = SERVER_STATES.get(level.getServer());
        return state == null ? null : state.entityCounts.get(level.dimension());
    }

    private static ServerRiftfallState serverState(MinecraftServer server) {
        return SERVER_STATES.computeIfAbsent(server, ignored -> new ServerRiftfallState());
    }

    private static final class ServerRiftfallState {
        private final Map<net.minecraft.resources.ResourceKey<Level>, RiftfallState> dimensions = new HashMap<>();
        private final Map<net.minecraft.resources.ResourceKey<Level>, RiftfallEntityCounts> entityCounts = new HashMap<>();
        private final Map<UUID, Float> playerExposure = new HashMap<>();
    }

    private static final class RiftfallEntityCounts {
        private int riftborn;
        private int riftListeners;
        private int riftboundWraiths;
    }

    private static final class RiftfallState {
        private RiftfallStage stage = RiftfallStage.CLEAR;
        private int stageTicksRemaining = 0;
        private int cooldownTicksRemaining = 0;
    }
}
