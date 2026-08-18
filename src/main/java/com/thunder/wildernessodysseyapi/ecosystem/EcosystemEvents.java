package com.thunder.wildernessodysseyapi.ecosystem;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.EcosystemBehaviorGoal;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.GroupFollowerGoal;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.WildlifeDisturbancePolicy;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.debug.EnvironmentalMemoryDebugSync;
import com.thunder.wildernessodysseyapi.ecosystem.memory.DisturbanceSource;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * NeoForge integration points for installing controllers and recording disturbances.
 *
 * <p>Entity join only attaches the goal; it performs no world query because the
 * event can fire before the underlying chunk reaches FULL status.</p>
 */
public final class EcosystemEvents {

    private static final int ECOSYSTEM_GOAL_PRIORITY = 2;
    private static final int GROUP_FOLLOW_GOAL_PRIORITY = 3;
    private static final long MOVEMENT_SAMPLE_INTERVAL_TICKS = 40L;
    private static final long DEBUG_SYNC_INTERVAL_TICKS = 20L;
    private static final double MINIMUM_RECORDED_MOVEMENT_SQUARED = 16.0;
    private static final Map<UUID, PlayerTrafficSample> PLAYER_TRAFFIC = new HashMap<>();

    private EcosystemEvents() {
    }

    /** Adds one conditional goal to server animals that have a loaded profile. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof PathfinderMob animal)) {
            return;
        }
        installController(animal);
    }

    /** Installs controllers on already-loaded mobs after a data-pack profile reload. */
    public static void refreshLoadedControllers(MinecraftServer server) {
        EcosystemServices.groups().clearAll();
        if (!EcosystemConfig.ENABLED.get()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PathfinderMob animal) {
                    installController(animal);
                }
            }
        }
    }

    private static void installController(PathfinderMob animal) {
        if (!EcosystemConfig.ENABLED.get() || SpeciesBehaviorProfileManager.profileFor(animal).isEmpty()) {
            return;
        }
        AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
        if (needs.controllerInstalled()) {
            return;
        }
        animal.goalSelector.addGoal(ECOSYSTEM_GOAL_PRIORITY, new EcosystemBehaviorGoal(animal));
        animal.goalSelector.addGoal(GROUP_FOLLOW_GOAL_PRIORITY, new GroupFollowerGoal(animal));
        needs.markControllerInstalled();
        needs.scheduleEvaluation(animal.level().getGameTime() + Math.floorMod(animal.getId(), 40));
    }

    /**
     * Samples moving players once every two seconds without scanning the player list.
     *
     * <p>The player tick is already emitted by NeoForge. Most invocations do a
     * UUID lookup and two timestamp comparisons; world data changes only after
     * a player moves at least four blocks from the previous recorded position.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        PlayerTrafficSample sample = PLAYER_TRAFFIC.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerTrafficSample(
                        level.getServer(), level.dimension(), player.blockPosition(), gameTime)
        );
        if (sample.server != level.getServer() || !sample.dimension.equals(level.dimension())) {
            sample.reset(level.getServer(), level.dimension(), player.blockPosition(), gameTime);
        }

        if (gameTime >= sample.nextMovementSampleAt) {
            BlockPos current = player.blockPosition();
            if (!player.isSpectator()
                    && current.distSqr(sample.lastRecordedPosition) >= MINIMUM_RECORDED_MOVEMENT_SQUARED) {
                double amount = EcosystemConfig.MOVEMENT_DISTURBANCE.get();
                if (EcosystemConfig.ENABLED.get() && amount > 0.0) {
                    EnvironmentalMemoryManager.addDisturbance(
                            level, current, amount, DisturbanceSource.PLAYER_MOVEMENT, player.getUUID());
                }
                sample.lastRecordedPosition = current.immutable();
            }
            sample.nextMovementSampleAt = gameTime + MOVEMENT_SAMPLE_INTERVAL_TICKS;
        }

        // Server memory reaches the existing F3 page only while diagnostics are explicitly enabled.
        if (EcosystemConfig.DEBUG_COMMANDS_ENABLED.get() && gameTime >= sample.nextDebugSyncAt) {
            EnvironmentalMemoryDebugSync.send(player);
            sample.nextDebugSyncAt = gameTime + DEBUG_SYNC_INTERVAL_TICKS;
        }
    }

    /** Records successful combat in persistent regional memory and wakes a profiled victim's controller. */
    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        if (!EcosystemConfig.ENABLED.get()
                || !(event.getEntity().level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F) {
            return;
        }
        Entity source = event.getSource().getEntity();
        double severity = Math.min(1.5,
                0.5 + event.getNewDamage() / Math.max(1.0F, event.getEntity().getMaxHealth()));
        double amount = EcosystemConfig.COMBAT_DISTURBANCE.get() * severity;
        if (amount > 0.0) {
            EcosystemServices.disturbances().record(
                    level,
                    event.getEntity().blockPosition(),
                    source == null ? null : source.getUUID(),
                    amount,
                    DisturbanceSource.COMBAT
            );
        }
        if (event.getEntity() instanceof PathfinderMob animal) {
            Optional<SpeciesBehaviorProfile> profile = SpeciesBehaviorProfileManager.profileFor(animal);
            if (profile.isEmpty()) {
                return;
            }
            AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
            if (source != null) {
                long expiresAt = level.getGameTime() + Math.max(20, profile.get().prey().threatMemoryTicks());
                needs.rememberThreat(source.blockPosition(), source.getUUID(), expiresAt);
                if (profile.get().prey().enabled()) {
                    EcosystemServices.groups().reportThreat(
                            animal,
                            new EnvironmentalContext.Threat(
                                    source.blockPosition(),
                                    source.getUUID(),
                                    animal.distanceToSqr(source),
                                    expiresAt
                            ),
                            EcosystemConfig.GROUP_FORMATION_RADIUS.get()
                    );
                }
            }
            needs.scheduleEvaluation(level.getGameTime() + 1L);
        }
    }

    /** Removes cached membership immediately when an animal dies, unloads, or changes level. */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof PathfinderMob animal) {
            EcosystemServices.groups().onEntityLeave(level, animal);
        }
    }

    /** Records successful player block breaking as configurable regional activity. */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        double amount = EcosystemConfig.PLAYER_ACTIVITY_DISTURBANCE.get();
        if (EcosystemConfig.ENABLED.get()
                && !event.isCanceled()
                && amount > 0.0
                && event.getLevel() instanceof ServerLevel level) {
            EcosystemServices.disturbances().record(
                    level,
                    event.getPos(),
                    event.getPlayer().getUUID(),
                    amount,
                    DisturbanceSource.PLAYER_ACTIVITY
            );
        }
    }

    /** Records successful player building through the same bounded activity source. */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        double amount = EcosystemConfig.PLAYER_ACTIVITY_DISTURBANCE.get();
        if (EcosystemConfig.ENABLED.get()
                && !event.isCanceled()
                && amount > 0.0
                && event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level) {
            EcosystemServices.disturbances().record(
                    level,
                    event.getPos(),
                    player.getUUID(),
                    amount,
                    DisturbanceSource.PLAYER_ACTIVITY
            );
        }
    }

    /** Records one event at an explosion center, scaled by blast radius but capped by the memory ledger. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        double configured = EcosystemConfig.EXPLOSION_DISTURBANCE.get();
        if (!EcosystemConfig.ENABLED.get()
                || configured <= 0.0
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        double scale = Math.max(0.5, Math.min(2.0, event.getExplosion().radius() / 4.0));
        Entity source = event.getExplosion().getDirectSourceEntity();
        EnvironmentalMemoryManager.addDisturbance(
                level,
                BlockPos.containing(event.getExplosion().center()),
                configured * scale,
                DisturbanceSource.EXPLOSION,
                source == null ? null : source.getUUID()
        );
    }

    /** Reduces only natural and chunk-generation spawns for profiled wildlife; chance never reaches zero. */
    @SubscribeEvent
    public static void onWildlifePositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!EcosystemConfig.ENABLED.get()
                || (event.getSpawnType() != MobSpawnType.NATURAL
                && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION)
                || !(event.getEntity() instanceof PathfinderMob animal)
                || SpeciesBehaviorProfileManager.profileFor(animal).isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        double disturbance = EnvironmentalMemoryManager.getDisturbance(
                level, BlockPos.containing(event.getX(), event.getY(), event.getZ()));
        double chance = WildlifeDisturbancePolicy.spawnChance(disturbance);
        if (level.getRandom().nextDouble() >= chance) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** Releases the constant-size per-player movement baseline on logout. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_TRAFFIC.remove(event.getEntity().getUUID());
    }

    /** Releases world-derived caches when a server dimension unloads. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EcosystemServices.clear(level);
        }
    }

    private static final class PlayerTrafficSample {
        private MinecraftServer server;
        private ResourceKey<Level> dimension;
        private BlockPos lastRecordedPosition;
        private long nextMovementSampleAt;
        private long nextDebugSyncAt;

        private PlayerTrafficSample(
                MinecraftServer server,
                ResourceKey<Level> dimension,
                BlockPos position,
                long gameTime
        ) {
            reset(server, dimension, position, gameTime);
        }

        private void reset(
                MinecraftServer server,
                ResourceKey<Level> dimension,
                BlockPos position,
                long gameTime
        ) {
            this.server = server;
            this.dimension = dimension;
            this.lastRecordedPosition = position.immutable();
            this.nextMovementSampleAt = gameTime + MOVEMENT_SAMPLE_INTERVAL_TICKS;
            this.nextDebugSyncAt = gameTime;
        }
    }
}
