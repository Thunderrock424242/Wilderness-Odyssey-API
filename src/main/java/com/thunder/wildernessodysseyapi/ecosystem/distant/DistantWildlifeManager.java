package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.network.DistantWildlifeSyncPayload;
import com.thunder.wildernessodysseyapi.ecosystem.integration.EcosystemPerformanceIntegration;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemEntitySafety;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationSettings;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-thread authority for abstract wildlife movement and entity transitions.
 *
 * <p>The manager scans only at the configured interval, advances at most one
 * object per group, and materializes only the configured number of entities in
 * any one tick. It never loads a chunk for habitat, movement, or spawning work.</p>
 */
public final class DistantWildlifeManager {
    private static final DistantWildlifeManager INSTANCE = new DistantWildlifeManager();
    private static final int MATERIALIZATION_RETRY_TICKS = 20;
    private static final int MATERIALIZATION_INTERVAL_TICKS = 5;
    private static final int TRANSITION_SYNC_INTERVAL_TICKS = 5;
    private static final int SPAWN_ATTEMPTS_PER_ANIMAL = 12;
    private static final int DISTURBANCE_RADIUS = 64;
    private static final double VIEW_CONE_DOT = 0.72;

    private final Map<ServerLevel, RuntimeState> runtimes = new WeakHashMap<>();
    private final Map<ServerPlayer, Boolean> playerSyncEnabled = new WeakHashMap<>();
    private long nextSequence = 1L;

    private DistantWildlifeManager() {
    }

    /** Returns the single server-thread manager shared by all dimensions. */
    public static DistantWildlifeManager get() {
        return INSTANCE;
    }

    /** Advances bounded transition work from the ecosystem Data Engine adapter. */
    public void tick(MinecraftServer server) {
        EcosystemConfig.DistantWildlifeSettings settings = EcosystemConfig.distantWildlifeSettings();
        long serverTick = server.getTickCount();

        for (ServerLevel level : server.getAllLevels()) {
            RuntimeState runtime = runtime(level);
            boolean groupUpdateDue = intervalElapsed(
                    serverTick,
                    runtime.lastPopulationUpdateTick,
                    settings.updateInterval()
            );
            if (settings.enabled() && intervalElapsed(
                    serverTick,
                    runtime.lastMaterializationTick,
                    MATERIALIZATION_INTERVAL_TICKS
            )) {
                runtime.lastMaterializationTick = serverTick;
                long started = System.nanoTime();
                int materializedBefore = runtime.materializedSinceUpdate;
                materializeNearbyGroups(
                        level,
                        settings,
                        EcosystemSimulationSettings.fromConfig().entityTransitionRate(),
                        runtime
                );
                if (runtime.materializedSinceUpdate > materializedBefore) {
                    runtime.transitionSyncDirty = true;
                }
                if (runtime.transitionSyncDirty
                        && !groupUpdateDue
                        && intervalElapsed(
                                serverTick,
                                runtime.lastTransitionSyncTick,
                                TRANSITION_SYNC_INTERVAL_TICKS
                        )) {
                    runtime.transitionPacketsSinceUpdate += syncEnabled(
                            level, DistantWildlifeSavedData.get(level), settings
                    );
                    runtime.transitionSyncDirty = false;
                    runtime.lastTransitionSyncTick = serverTick;
                }
                EcosystemSimulationManager.get().recordExternalWork(
                        level, level.getGameTime(), System.nanoTime() - started
                );
            }

            if (groupUpdateDue) {
                runtime.lastPopulationUpdateTick = serverTick;
                if (settings.enabled()) {
                    updateEnabledLevel(level, settings);
                } else {
                    syncDisabled(level, settings);
                }
            }
        }
    }

    /** Forces a fresh enabled/disabled snapshot on the next configured pass. */
    public void markPlayerDirty(ServerPlayer player) {
        playerSyncEnabled.remove(player);
        EcosystemPerformanceIntegration.markClientStateDirty("distant wildlife player lifecycle changed");
    }

    /** Marks every connected player dirty after a server config reload. */
    public void markAllPlayersDirty(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                markPlayerDirty(player);
            }
        }
    }

    /** Requests a bounded corrective snapshot after an ecology-owned count change. */
    public void markPopulationChanged(ServerLevel level) {
        runtime(level).transitionSyncDirty = true;
    }

    /** Releases per-player state immediately after disconnect. */
    public void forgetPlayer(ServerPlayer player) {
        playerSyncEnabled.remove(player);
    }

    /** Releases only transient state for an unloading dimension. */
    public void unload(ServerLevel level) {
        runtimes.remove(level);
    }

    /** Clears all process-scoped cursors after worlds have saved. */
    public void shutdown() {
        runtimes.clear();
        playerSyncEnabled.clear();
        nextSequence = 1L;
    }

    /** Returns the latest bounded server diagnostics without forcing SavedData creation. */
    public Diagnostics diagnostics(ServerLevel level) {
        RuntimeState runtime = runtimes.get(level);
        return runtime == null ? Diagnostics.EMPTY : runtime.diagnostics;
    }

    /** Sends only player snapshots invalidated by login, respawn, travel, or config changes. */
    public int syncDirtyPlayers(MinecraftServer server) {
        EcosystemConfig.DistantWildlifeSettings settings = EcosystemConfig.distantWildlifeSettings();
        int packets = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (settings.enabled()) {
                int levelPackets = syncEnabled(
                        level,
                        DistantWildlifeSavedData.get(level),
                        settings,
                        true
                );
                RuntimeState runtime = runtime(level);
                runtime.transitionPacketsSinceUpdate += levelPackets;
                if (levelPackets > 0) {
                    runtime.lastPlayerSyncTick = level.getGameTime();
                }
                packets += levelPackets;
            } else {
                packets += syncDisabled(level, settings);
            }
        }
        return packets;
    }

    private void updateEnabledLevel(
            ServerLevel level,
            EcosystemConfig.DistantWildlifeSettings settings
    ) {
        long started = System.nanoTime();
        RuntimeState runtime = runtime(level);
        DistantWildlifeSavedData data = DistantWildlifeSavedData.get(level);
        int absorbed = absorbEligibleEntities(
                level,
                data,
                settings,
                EcosystemSimulationSettings.fromConfig().entityTransitionRate(),
                runtime
        );
        advanceGroups(level, data, settings);
        int currentSyncPackets = runtime.lastPlayerSyncTick == level.getGameTime()
                ? 0
                : syncEnabled(level, data, settings);
        int packets = currentSyncPackets + runtime.transitionPacketsSinceUpdate;
        runtime.transitionSyncDirty = false;
        runtime.diagnostics = new Diagnostics(
                true,
                data.groups().size(),
                data.representedAnimals(),
                absorbed,
                runtime.materializedSinceUpdate,
                packets,
                (System.nanoTime() - started) / 1_000L,
                settings.updateInterval(),
                settings.realEntityDistance(),
                settings.distantWildlifeDistance(),
                settings.transitionBuffer()
        );
        EcosystemSimulationManager.get().recordExternalWork(
                level, level.getGameTime(), System.nanoTime() - started
        );
        runtime.materializedSinceUpdate = 0;
        runtime.transitionPacketsSinceUpdate = 0;
    }

    // Converts only conservative, unimportant, unobserved wildlife candidates.
    private int absorbEligibleEntities(
            ServerLevel level,
            DistantWildlifeSavedData data,
            EcosystemConfig.DistantWildlifeSettings settings,
            int transitionBudget,
            RuntimeState runtime
    ) {
        if (data.representedAnimals() >= settings.maximumRepresentedAnimals()) {
            return 0;
        }

        long gameTime = level.getGameTime();
        long minimumUnobservedTicks = Math.max(200L, settings.updateInterval() * 2L);
        Set<UUID> presentMobs = new HashSet<>();
        int absorbed = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof PathfinderMob animal)) {
                continue;
            }
            presentMobs.add(animal.getUUID());
            if (!isSafeToAbstract(animal, gameTime, runtime)) {
                runtime.unobservedSince.remove(animal.getUUID());
                continue;
            }

            Optional<SpeciesBehaviorProfile> profile = SpeciesBehaviorProfileManager.profileFor(animal);
            if (profile.isEmpty()) {
                runtime.unobservedSince.remove(animal.getUUID());
                continue;
            }
            var simulationLevel = EcosystemSimulationManager.get().getSimulationLevel(
                    level, animal.blockPosition()
            );
            if (simulationLevel != WildlifeSimulationLod.DISTANT
                    && simulationLevel != WildlifeSimulationLod.DORMANT) {
                runtime.unobservedSince.remove(animal.getUUID());
                continue;
            }
            PlayerObservation observation = observePlayers(level.players(), animal.position(), animal.getBbHeight() * 0.5);
            if (observation.potentiallyObserved) {
                runtime.unobservedSince.put(animal.getUUID(), gameTime);
                continue;
            }
            long unobservedSince = runtime.unobservedSince.computeIfAbsent(animal.getUUID(), ignored -> gameTime);
            if (!DistantWildlifeTransitionPolicy.canAbstract(
                    observation.closestDistance,
                    false,
                    gameTime - unobservedSince,
                    settings.realEntityDistance(),
                    settings.transitionBuffer(),
                    minimumUnobservedTicks
            )) {
                continue;
            }

            ResourceLocation species = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
            Vec3 movement = animal.getDeltaMovement();
            double directionX = movement.x;
            double directionZ = movement.z;
            if (Math.hypot(directionX, directionZ) < 1.0E-4) {
                double yawRadians = Math.toRadians(animal.getYRot());
                directionX = -Math.sin(yawRadians);
                directionZ = Math.cos(yawRadians);
            }
            DistantWildlifeForm form = formOf(animal);
            long seed = level.getSeed() ^ animal.getUUID().getMostSignificantBits()
                    ^ Long.rotateLeft(animal.getUUID().getLeastSignificantBits(), 17);
            if (!data.absorb(
                    species,
                    animal.position(),
                    directionX,
                    directionZ,
                    cruiseSpeed(form, profile.get()),
                    seed,
                    gameTime,
                    form,
                    profile.get().needs().nocturnal(),
                    profile.get().shelter().enabled(),
                    settings.maximumGroups(),
                    settings.maximumRepresentedAnimals()
            )) {
                continue;
            }

            // Population ownership moved first; discard now prevents the chunk
            // from saving a duplicate real form of the same animal.
            animal.discard();
            runtime.unobservedSince.remove(animal.getUUID());
            absorbed++;
            if (absorbed >= Math.max(1, transitionBudget)) {
                break;
            }
            if (data.representedAnimals() >= settings.maximumRepresentedAnimals()) {
                break;
            }
        }
        runtime.unobservedSince.keySet().retainAll(presentMobs);
        runtime.materializedUntil.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        runtime.materializationRetryAfter.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        return absorbed;
    }

    // Evaluates movement, weather, habitat, and disturbance once per whole group.
    private void advanceGroups(
            ServerLevel level,
            DistantWildlifeSavedData data,
            EcosystemConfig.DistantWildlifeSettings settings
    ) {
        long gameTime = level.getGameTime();
        for (DistantWildlifeGroup group : data.groups()) {
            Vec3 current = group.positionAt(gameTime);
            BlockPos currentPos = BlockPos.containing(current);
            var simulationLevel = EcosystemSimulationManager.get().getSimulationLevel(level, currentPos);
            if (simulationLevel == WildlifeSimulationLod.DORMANT) {
                if (group.activityScale() != 0.0) {
                    data.replace(group.withMotion(
                            current, group.directionX(), group.directionZ(), 0.0, gameTime
                    ));
                }
                continue;
            }
            boolean currentChunkLoaded = isChunkLoaded(level, currentPos);
            RegionalEnvironmentSnapshot regionalEnvironment = currentChunkLoaded
                    ? EnvironmentServices.query().sample(level, currentPos)
                    : RegionalEnvironmentSnapshot.EMPTY;
            WeatherSample weather = regionalEnvironment.weather();
            Optional<EnvironmentalContext.Disturbance> disturbance = EcosystemServices.disturbances().nearest(
                    level, currentPos, DISTURBANCE_RADIUS, gameTime
            );
            double disturbanceIntensity = Math.max(
                    disturbance.map(EnvironmentalContext.Disturbance::intensity).orElse(0.0),
                    EnvironmentalMemoryManager.getMemory(level, currentPos)
                            .map(memory -> memory.strongestActivity())
                            .orElse(0.0)
            );
            double activityScale = DistantWildlifeActivityPolicy.movementScale(
                    group.nocturnal(),
                    level.getDayTime(),
                    group.weatherSensitive(),
                    weather.precipitationIntensity(),
                    weather.thunderIntensity(),
                    disturbanceIntensity
            );
            activityScale *= 0.35
                    + regionalEnvironment.influence().wildlifeActivity() * 0.65;
            if (group.form() == DistantWildlifeForm.AQUATIC
                    && regionalEnvironment.coastal()) {
                activityScale *= 0.70
                        + regionalEnvironment.influence().aquaticActivity() * 0.45;
            }

            double directionX = group.directionX();
            double directionZ = group.directionZ();
            if (disturbance.isPresent()) {
                directionX = current.x - disturbance.get().position().getX() - 0.5;
                directionZ = current.z - disturbance.get().position().getZ() - 0.5;
            } else {
                long bucket = gameTime / Math.max(1, settings.updateInterval());
                double turn = (unitHash(group.seed() ^ bucket) - 0.5) * 0.42;
                double angle = Math.atan2(directionZ, directionX) + turn;
                directionX = Math.cos(angle);
                directionZ = Math.sin(angle);
            }

            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(group.species()).orElse(null);
            Vec3 projectedDestination = current.add(
                    directionX * group.cruiseSpeed() * activityScale * settings.updateInterval() / 20.0,
                    0.0,
                    directionZ * group.cruiseSpeed() * activityScale * settings.updateInterval() / 20.0
            );
            BlockPos destinationPos = BlockPos.containing(projectedDestination);
            if (!isChunkLoaded(level, destinationPos)) {
                // Unknown terrain is never force-loaded just to move an abstract herd.
                activityScale = 0.0;
            } else if (entityType != null && !habitatAllows(level, entityType, destinationPos)) {
                directionX = -directionX;
                directionZ = -directionZ;
                activityScale *= 0.35;
            }

            // The projected destination validates the next interval; anchoring
            // there now would apply that interval twice and make clients jump.
            double anchorY = current.y;
            if (entityType != null && group.form() == DistantWildlifeForm.GROUND
                    && isChunkLoaded(level, currentPos)) {
                Heightmap.Types heightmap = SpawnPlacements.getHeightmapType(entityType);
                anchorY = level.getHeight(heightmap, currentPos.getX(), currentPos.getZ());
            }
            data.replace(group.withMotion(
                    new Vec3(current.x, anchorY, current.z),
                    directionX,
                    directionZ,
                    activityScale,
                    gameTime
            ));
        }
    }

    // Materialization is more responsive than network/group updates but remains bounded.
    private void materializeNearbyGroups(
            ServerLevel level,
            EcosystemConfig.DistantWildlifeSettings settings,
            int transitionBudget,
            RuntimeState runtime
    ) {
        if (level.players().isEmpty()) {
            return;
        }
        DistantWildlifeSavedData data = DistantWildlifeSavedData.get(level);
        long gameTime = level.getGameTime();
        int remainingBudget = Math.max(1, transitionBudget);
        for (DistantWildlifeGroup group : data.groups()) {
            if (runtime.materializationRetryAfter.getOrDefault(group.id(), 0L) > gameTime) {
                continue;
            }
            Vec3 current = group.positionAt(gameTime);
            PlayerObservation observation = observePlayers(level.players(), current, 0.8);
            WildlifeSimulationLod simulationLevel = EcosystemSimulationManager.get()
                    .getSimulationLevel(level, BlockPos.containing(current));
            if (simulationLevel != WildlifeSimulationLod.ACTIVE
                    && !DistantWildlifeTransitionPolicy.shouldMaterialize(
                    observation.closestDistance,
                    settings.realEntityDistance(),
                    settings.transitionBuffer()
                    )) {
                continue;
            }
            int count = Math.min(group.populationEstimate(), remainingBudget);
            for (int index = 0; index < count; index++) {
                Mob spawned = tryMaterialize(level, group, current, observation.closestDistance, settings, index);
                if (spawned == null) {
                    runtime.materializationRetryAfter.put(
                            group.id(), gameTime + MATERIALIZATION_RETRY_TICKS
                    );
                    break;
                }
                if (!data.materializedOne(group.id())) {
                    spawned.discard();
                    break;
                }
                runtime.materializedUntil.put(
                        spawned.getUUID(),
                        gameTime + Math.max(200L, settings.updateInterval() * 2L)
                );
                runtime.materializationRetryAfter.remove(group.id());
                runtime.materializedSinceUpdate++;
                remainingBudget--;
                if (remainingBudget == 0) {
                    return;
                }
            }
        }
    }

    private Mob tryMaterialize(
            ServerLevel level,
            DistantWildlifeGroup group,
            Vec3 groupPosition,
            double closestPlayerDistance,
            EcosystemConfig.DistantWildlifeSettings settings,
            int materializationIndex
    ) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(group.species()).orElse(null);
        if (type == null) {
            return null;
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            return null;
        }

        boolean emergencyVisibleSpawn = closestPlayerDistance
                <= Math.max(16.0, settings.realEntityDistance() - settings.transitionBuffer());
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS_PER_ANIMAL; attempt++) {
            long hash = mix64(group.seed()
                    ^ ((long) materializationIndex << 32)
                    ^ attempt
                    ^ (level.getGameTime() / MATERIALIZATION_RETRY_TICKS));
            double angle = unitHash(hash) * Math.PI * 2.0;
            double radius = 5.0 + unitHash(Long.rotateLeft(hash, 21)) * 18.0;
            int x = (int) Math.floor(groupPosition.x + Math.cos(angle) * radius);
            int z = (int) Math.floor(groupPosition.z + Math.sin(angle) * radius);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                continue;
            }
            BlockPos spawnPos = spawnPosition(level, type, group, x, z, hash);
            if (spawnPos == null) {
                continue;
            }
            Vec3 spawnCenter = Vec3.atBottomCenterOf(spawnPos);
            if (!emergencyVisibleSpawn && observePlayers(level.players(), spawnCenter, mob.getBbHeight() * 0.5).potentiallyObserved) {
                continue;
            }

            float yaw = (float) (Math.toDegrees(Math.atan2(-group.directionX(), group.directionZ())));
            mob.moveTo(spawnCenter.x, spawnCenter.y, spawnCenter.z, yaw, 0.0F);
            if (!mob.checkSpawnRules(level, MobSpawnType.EVENT) || !mob.checkSpawnObstruction(level)) {
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
            if (level.addFreshEntity(mob)) {
                return mob;
            }
            break;
        }
        mob.discard();
        return null;
    }

    private static BlockPos spawnPosition(
            ServerLevel level,
            EntityType<?> type,
            DistantWildlifeGroup group,
            int x,
            int z,
            long hash
    ) {
        if (group.form() == DistantWildlifeForm.AQUATIC) {
            int centerY = (int) Math.floor(group.anchorY());
            for (int offset = 0; offset <= 6; offset++) {
                int signedOffset = (offset & 1) == 0 ? offset / 2 : -(offset + 1) / 2;
                BlockPos candidate = new BlockPos(x, centerY + signedOffset, z);
                if (!level.getFluidState(candidate).isEmpty()) {
                    return candidate;
                }
            }
            return null;
        }

        int groundY = level.getHeight(SpawnPlacements.getHeightmapType(type), x, z);
        if (group.form() == DistantWildlifeForm.FLYING) {
            int altitude = 4 + (int) Math.floor(unitHash(Long.rotateLeft(hash, 9)) * 7.0);
            return new BlockPos(x, groundY + altitude, z);
        }
        return new BlockPos(x, groundY, z);
    }

    private int syncEnabled(
            ServerLevel level,
            DistantWildlifeSavedData data,
            EcosystemConfig.DistantWildlifeSettings settings
    ) {
        return syncEnabled(level, data, settings, false);
    }

    private int syncEnabled(
            ServerLevel level,
            DistantWildlifeSavedData data,
            EcosystemConfig.DistantWildlifeSettings settings,
            boolean dirtyOnly
    ) {
        int packets = 0;
        long gameTime = level.getGameTime();
        double maximumDistanceSquared = (double) settings.distantWildlifeDistance()
                * settings.distantWildlifeDistance();
        for (ServerPlayer player : level.players()) {
            if (dirtyOnly && playerSyncEnabled.containsKey(player)) {
                continue;
            }
            List<DistantWildlifeSyncPayload.GroupSnapshot> relevant = snapshotsForPlayer(
                    data,
                    player.position(),
                    gameTime,
                    maximumDistanceSquared,
                    settings.maximumRepresentedAnimals()
            );
            PacketDistributor.sendToPlayer(player, new DistantWildlifeSyncPayload(
                    level.dimension().location(),
                    DistantWildlifeSyncPayload.DATA_VERSION,
                    reserveSequence(),
                    true,
                    gameTime,
                    settings.realEntityDistance(),
                    settings.distantWildlifeDistance(),
                    settings.transitionBuffer(),
                    settings.updateInterval(),
                    relevant
            ));
            playerSyncEnabled.put(player, true);
            packets++;
        }
        return packets;
    }

    // Applies both server-config and wire-format caps before constructing a packet.
    private static List<DistantWildlifeSyncPayload.GroupSnapshot> snapshotsForPlayer(
            DistantWildlifeSavedData data,
            Vec3 playerPosition,
            long gameTime,
            double maximumDistanceSquared,
            int maximumRepresentedAnimals
    ) {
        List<DistantWildlifeSyncPayload.GroupSnapshot> snapshots = new ArrayList<>();
        int remainingAnimals = Math.min(
                maximumRepresentedAnimals,
                DistantWildlifeSyncPayload.MAXIMUM_REPRESENTED_ANIMALS
        );
        for (DistantWildlifeGroup group : data.groups()) {
            if (remainingAnimals == 0 || snapshots.size() >= DistantWildlifeSyncPayload.MAXIMUM_GROUPS) {
                break;
            }
            if (group.positionAt(gameTime).distanceToSqr(playerPosition) > maximumDistanceSquared) {
                continue;
            }
            int represented = Math.min(group.populationEstimate(), remainingAnimals);
            snapshots.add(DistantWildlifeSyncPayload.GroupSnapshot.fromGroup(group, represented));
            remainingAnimals -= represented;
        }
        return List.copyOf(snapshots);
    }

    private int syncDisabled(
            ServerLevel level,
            EcosystemConfig.DistantWildlifeSettings settings
    ) {
        int packets = 0;
        for (ServerPlayer player : level.players()) {
            if (Boolean.FALSE.equals(playerSyncEnabled.get(player))) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, DistantWildlifeSyncPayload.disabled(
                    level.dimension().location(),
                    reserveSequence(),
                    level.getGameTime(),
                    settings
            ));
            playerSyncEnabled.put(player, false);
            packets++;
        }
        return packets;
    }

    static boolean intervalElapsed(long currentTick, long lastTick, int intervalTicks) {
        return lastTick == Long.MIN_VALUE
                || currentTick < lastTick
                || currentTick - lastTick >= Math.max(1, intervalTicks);
    }

    private static boolean isSafeToAbstract(PathfinderMob animal, long gameTime, RuntimeState runtime) {
        if (!EcosystemEntitySafety.mayAbstract(animal)
                || !(animal instanceof Animal || animal instanceof WaterAnimal || animal instanceof FlyingAnimal)
                || !animal.isAlive()
                || animal.isRemoved()
                || !animal.getTags().isEmpty()
                || animal.getHealth() + 0.01F < animal.getMaxHealth()) {
            return false;
        }
        Long materializedUntil = runtime.materializedUntil.get(animal.getUUID());
        if (materializedUntil != null && materializedUntil > gameTime) {
            return false;
        }
        if (animal.getLastHurtByMob() != null
                && animal.tickCount - animal.getLastHurtByMobTimestamp() < 400) {
            return false;
        }
        if (animal instanceof AgeableMob ageable && ageable.isBaby()) {
            return false;
        }
        if (animal instanceof Animal breedingAnimal && breedingAnimal.isInLove()) {
            return false;
        }
        if (animal instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        return !(animal instanceof AbstractHorse horse) || !horse.isTamed();
    }

    private static PlayerObservation observePlayers(
            List<ServerPlayer> players,
            Vec3 position,
            double verticalOffset
    ) {
        double closestDistanceSquared = Double.POSITIVE_INFINITY;
        boolean potentiallyObserved = false;
        Vec3 target = position.add(0.0, verticalOffset, 0.0);
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            Vec3 toTarget = target.subtract(player.getEyePosition());
            double distanceSquared = toTarget.lengthSqr();
            closestDistanceSquared = Math.min(closestDistanceSquared, distanceSquared);
            if (distanceSquared < 1.0E-6) {
                potentiallyObserved = true;
                continue;
            }
            if (player.getLookAngle().dot(toTarget.normalize()) >= VIEW_CONE_DOT) {
                potentiallyObserved = true;
            }
        }
        return new PlayerObservation(Math.sqrt(closestDistanceSquared), potentiallyObserved);
    }

    private static DistantWildlifeForm formOf(PathfinderMob animal) {
        if (animal instanceof WaterAnimal) {
            return DistantWildlifeForm.AQUATIC;
        }
        return animal instanceof FlyingAnimal ? DistantWildlifeForm.FLYING : DistantWildlifeForm.GROUND;
    }

    private static double cruiseSpeed(
            DistantWildlifeForm form,
            SpeciesBehaviorProfile profile
    ) {
        double formSpeed = switch (form) {
            case GROUND -> 0.48;
            case FLYING -> 1.15;
            case AQUATIC -> 0.72;
        };
        double socialScale = profile.herd().enabled()
                ? Math.max(0.65, Math.min(1.35, profile.herd().moveSpeed()))
                : 0.82;
        return formSpeed * socialScale;
    }

    private static boolean habitatAllows(ServerLevel level, EntityType<?> type, BlockPos position) {
        if (!isChunkLoaded(level, position)) {
            return false;
        }
        return level.getBiome(position).value().getMobSettings().getMobs(type.getCategory()).unwrap().stream()
                .anyMatch(spawn -> spawn.type == type);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private RuntimeState runtime(ServerLevel level) {
        return runtimes.computeIfAbsent(level, ignored -> new RuntimeState());
    }

    private long reserveSequence() {
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Distant wildlife packet sequence space exhausted");
        }
        return nextSequence++;
    }

    private static double unitHash(long value) {
        return (mix64(value) >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    /** Latest per-level work and population counters used by operator diagnostics. */
    public record Diagnostics(
            boolean enabled,
            int groups,
            int representedAnimals,
            int absorbedLastUpdate,
            int materializedLastUpdate,
            int packetsLastUpdate,
            long lastUpdateMicros,
            int updateInterval,
            int realEntityDistance,
            int distantWildlifeDistance,
            int transitionBuffer
    ) {
        private static final Diagnostics EMPTY = new Diagnostics(
                false, 0, 0, 0, 0, 0, 0L, 0, 0, 0, 0
        );
    }

    private record PlayerObservation(double closestDistance, boolean potentiallyObserved) {
    }

    private static final class RuntimeState {
        private final Map<UUID, Long> unobservedSince = new HashMap<>();
        private final Map<UUID, Long> materializedUntil = new HashMap<>();
        private final Map<Long, Long> materializationRetryAfter = new HashMap<>();
        private Diagnostics diagnostics = Diagnostics.EMPTY;
        private int materializedSinceUpdate;
        private int transitionPacketsSinceUpdate;
        private boolean transitionSyncDirty;
        private long lastMaterializationTick = Long.MIN_VALUE;
        private long lastPopulationUpdateTick = Long.MIN_VALUE;
        private long lastTransitionSyncTick = Long.MIN_VALUE;
        private long lastPlayerSyncTick = Long.MIN_VALUE;
    }
}
