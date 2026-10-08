package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemEvents;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemParticipation;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeSavedData;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;

/**
 * Server-thread owner of nearest-player ecosystem simulation cells.
 *
 * <p>The manager tracks only cells that contain a player, profiled entity, or
 * abstract group. Empty cells are never enumerated across the configured
 * radius, so a very large render distance cannot create a quadratic scan. Real
 * wildlife and the distant group ledger remain single-authority forms: this
 * class chooses the level, while the existing distant manager commits the
 * actual form transitions.</p>
 */
public final class EcosystemSimulationManager {

    private static final EcosystemSimulationManager INSTANCE = new EcosystemSimulationManager();
    private static final int CANDIDATE_LIFECYCLE_CHECKS_PER_TICK = 128;

    private final Map<ServerLevel, LevelRuntime> runtimes = new WeakHashMap<>();

    private EcosystemSimulationManager() {
    }

    /** Returns the process-wide server simulation-zone authority. */
    public static EcosystemSimulationManager get() {
        return INSTANCE;
    }

    /** Advances immediate player-driven cell classification for every dimension. */
    public void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    /**
     * Runs periodic registered-wildlife scans from the bounded Data Engine path.
     *
     * <p>Lifecycle events maintain already-loaded wildlife candidates. A player entering a new
     * ecosystem cell still performs the immediate candidate pass in {@link #tick} so
     * manager-owned AI suspension cannot delay visible wildlife recovery.</p>
     */
    public void runOptionalMaintenance(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelRuntime runtime = runtimes.get(level);
            if (runtime == null || !runtime.wildlifeScanRequested) {
                continue;
            }
            EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
            if (!settings.enabled() || !EcosystemParticipation.isEnabled(level)) {
                runtime.wildlifeScanRequested = false;
                continue;
            }

            long gameTime = level.getGameTime();
            beginMetricsTick(runtime, gameTime);
            WildlifeScanMetrics scan = scanLoadedWildlife(level, runtime, settings);
            applyWildlifeScan(runtime, gameTime, scan);
            runtime.managerNanos += scan.elapsedNanos();
            int regionUpdates = runtime.metrics.tick() == gameTime
                    ? runtime.metrics.regionUpdates()
                    : 0;
            publishMetrics(level, runtime, gameTime, regionUpdates);
        }
    }

    /** Returns the cached coarse level at a position, classifying a new cell on demand. */
    public WildlifeSimulationLod getSimulationLevel(ServerLevel level, BlockPos position) {
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        if (!settings.enabled() || !EcosystemParticipation.isEnabled(level)) {
            return WildlifeSimulationLod.ACTIVE;
        }
        LevelRuntime runtime = runtime(level);
        if (!runtime.playersInitialized) {
            refreshPlayers(level, runtime, settings);
        }
        EcosystemCellKey key = EcosystemCellKey.fromBlock(position, settings.cellSize());
        WildlifeSimulationLod simulationLevel = EcosystemZoneClassifier.classifyCell(key, runtime.players, settings);
        runtime.putCell(key.packed(), simulationLevel);
        return simulationLevel;
    }

    /**
     * Classifies one position for a bounded diagnostic view without retaining
     * an otherwise-empty ecosystem cell.
     *
     * <p>This server-thread method uses the manager's current connected-player
     * points, initializing them once when necessary, then applies the same owner
     * policy as normal cell classification. It does not inspect entities, load
     * chunks, or add the sampled cell to the relevance cache.</p>
     */
    public WildlifeSimulationLod previewSimulationLevel(ServerLevel level, BlockPos position) {
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        if (!settings.enabled() || !EcosystemParticipation.isEnabled(level)) {
            return WildlifeSimulationLod.ACTIVE;
        }
        LevelRuntime runtime = runtime(level);
        if (!runtime.playersInitialized) {
            refreshPlayers(level, runtime, settings);
        }
        EcosystemCellKey key = EcosystemCellKey.fromBlock(position, settings.cellSize());
        return EcosystemZoneClassifier.classifyCell(key, runtime.players, settings);
    }

    /** Returns whether the cell uses the complete individual ecosystem behavior layer. */
    public boolean isFullySimulated(ServerLevel level, BlockPos position) {
        return EcosystemParticipation.isEnabled(level)
                && getSimulationLevel(level, position) == WildlifeSimulationLod.ACTIVE;
    }

    /** Returns exact horizontal distance to the nearest alive, non-spectating player. */
    public double getNearestPlayerDistance(ServerLevel level, BlockPos position) {
        if (!EcosystemParticipation.isEnabled(level)) {
            return Double.POSITIVE_INFINITY;
        }
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        LevelRuntime runtime = runtime(level);
        if (!runtime.playersInitialized) {
            refreshPlayers(level, runtime, settings);
        }
        return EcosystemZoneClassifier.nearestDistance(
                position.getX() + 0.5,
                position.getZ() + 0.5,
                runtime.players
        );
    }

    /** Prioritizes one cell for reclassification without doing immediate world work. */
    public void requestRegionalUpdate(ServerLevel level, BlockPos position) {
        if (!EcosystemParticipation.isEnabled(level)) {
            return;
        }
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        LevelRuntime runtime = runtime(level);
        queueFirst(runtime, EcosystemCellKey.fromBlock(position, settings.cellSize()).packed());
    }

    /** Returns an immutable aggregate of the abstract groups currently occupying one cell. */
    public Optional<EcosystemRegionSnapshot> getRegionSnapshot(ServerLevel level, BlockPos position) {
        if (!EcosystemParticipation.isEnabled(level)) {
            return Optional.empty();
        }
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        EcosystemCellKey key = EcosystemCellKey.fromBlock(position, settings.cellSize());
        Map<ResourceLocation, Integer> populations = new HashMap<>();
        int groups = 0;
        double food = 0.0;
        double water = 0.0;
        double pressure = 0.0;
        double disturbance = 0.0;
        double weather = 0.0;
        double directionX = 0.0;
        double directionZ = 0.0;
        long lastUpdated = 0L;
        long gameTime = level.getGameTime();
        for (DistantWildlifeGroup group : DistantWildlifeSavedData.get(level).groups()) {
            if (!key.equals(EcosystemCellKey.fromBlock(
                    BlockPos.containing(group.positionAt(gameTime)), settings.cellSize()))) {
                continue;
            }
            populations.merge(group.species(), group.populationEstimate(), Integer::sum);
            groups++;
            food += group.foodAvailability();
            water += group.waterAvailability();
            pressure += group.foodPressure();
            disturbance += group.disturbance();
            weather += group.weatherImpact();
            directionX += group.directionX();
            directionZ += group.directionZ();
            lastUpdated = Math.max(lastUpdated, group.populationReferenceGameTime());
        }
        if (groups == 0) {
            return Optional.empty();
        }
        EcosystemCellKey migrationTarget = new EcosystemCellKey(
                key.x() + Integer.compare((int) Math.signum(directionX), 0),
                key.z() + Integer.compare((int) Math.signum(directionZ), 0)
        );
        return Optional.of(new EcosystemRegionSnapshot(
                key,
                getSimulationLevel(level, position),
                populations,
                groups,
                migrationTarget,
                food / groups,
                water / groups,
                pressure / groups,
                disturbance / groups,
                weather / groups,
                lastUpdated,
                gameTime
        ));
    }

    /**
     * Reorients abstract groups in one cell toward a future migration target.
     *
     * @return how many persisted groups changed direction
     */
    public int requestMigration(ServerLevel level, BlockPos origin, BlockPos target) {
        if (!EcosystemParticipation.isEnabled(level)) {
            return 0;
        }
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        EcosystemCellKey sourceKey = EcosystemCellKey.fromBlock(origin, settings.cellSize());
        long gameTime = level.getGameTime();
        DistantWildlifeSavedData data = DistantWildlifeSavedData.get(level);
        int updated = 0;
        for (DistantWildlifeGroup group : data.groups()) {
            Vec3 current = group.positionAt(gameTime);
            if (!sourceKey.equals(EcosystemCellKey.fromBlock(
                    BlockPos.containing(current), settings.cellSize()))) {
                continue;
            }
            double dx = target.getX() + 0.5 - current.x;
            double dz = target.getZ() + 0.5 - current.z;
            if (Math.hypot(dx, dz) < 1.0) {
                continue;
            }
            if (data.replace(group.withMotion(current, dx, dz, group.activityScale(), gameTime))) {
                updated++;
            }
        }
        if (updated > 0) {
            requestRegionalUpdate(level, origin);
            requestRegionalUpdate(level, target);
        }
        return updated;
    }

    /** Adds one measured entity-evaluation duration to the current ecosystem tick. */
    public void recordEntityEvaluation(ServerLevel level, long gameTime, long elapsedNanos) {
        if (!EcosystemParticipation.isEnabled(level)) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        beginMetricsTick(runtime, gameTime);
        runtime.entityEvaluationNanos += Math.max(0L, elapsedNanos);
    }

    /** Adds work performed by the delegated distant-group transition owner. */
    public void recordExternalWork(ServerLevel level, long gameTime, long elapsedNanos) {
        if (!EcosystemParticipation.isEnabled(level)) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        beginMetricsTick(runtime, gameTime);
        long safeElapsed = Math.max(0L, elapsedNanos);
        runtime.externalNanos += safeElapsed;

        // Distant population work runs after the zone pass in the post-tick
        // event. Amend an already-published snapshot so this time is not lost
        // when the next game tick resets the accumulator.
        if (runtime.metrics.tick() == gameTime) {
            EcosystemSimulationMetrics.Snapshot previous = runtime.metrics;
            runtime.metrics = new EcosystemSimulationMetrics.Snapshot(
                    previous.tick(),
                    previous.activeCells(),
                    previous.nearCells(),
                    previous.distantCells(),
                    previous.dormantCells(),
                    previous.fullySimulatedEntityCount(),
                    previous.abstractPopulationCount(),
                    previous.regionUpdates(),
                    previous.pendingRegionalUpdates(),
                    previous.wildlifeScanTick(),
                    previous.scannedLoadedEntityCount(),
                    previous.profiledWildlifeCount(),
                    previous.wildlifeScanNanos(),
                    previous.updateNanos() + safeElapsed
            );
        }
    }

    /** Returns the most recent per-dimension performance snapshot. */
    public EcosystemSimulationMetrics.Snapshot metrics(ServerLevel level) {
        LevelRuntime runtime = runtimes.get(level);
        return runtime == null ? EcosystemSimulationMetrics.Snapshot.EMPTY : runtime.metrics;
    }

    /** Reclassifies current worlds after a server-config reload. */
    public void onConfigurationReload(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelRuntime runtime = runtime(level);
            runtime.lastCoverageRefresh = Long.MIN_VALUE;
            runtime.lastPlayerCellHash = Long.MIN_VALUE;
            if (!EcosystemSimulationSettings.fromConfig().enabled() || !EcosystemParticipation.isEnabled(level)) {
                resumeAll(level);
                runtime.clearCells();
                runtime.simulationEnabled = false;
            } else {
                refreshPlayers(level, runtime, EcosystemSimulationSettings.fromConfig());
                WildlifeScanMetrics scan = scanLoadedWildlife(level, runtime, EcosystemSimulationSettings.fromConfig());
                applyWildlifeScan(runtime, level.getGameTime(), scan);
            }
        }
    }

    /** Releases only transient caches for an unloading dimension. */
    public void unload(ServerLevel level) {
        LevelRuntime runtime = runtimes.get(level);
        if (runtime != null) {
            resumeAll(level);
        }
        runtimes.remove(level);
    }

    /** Registers loaded wildlife without querying blocks or requiring a FULL chunk. */
    public void onEntityJoin(ServerLevel level, PathfinderMob animal) {
        onEntityJoin(level, animal, null);
    }

    /** Retains the cancellable notification until a consumer confirms server ownership. */
    public void onEntityJoin(ServerLevel level, PathfinderMob animal, EntityJoinLevelEvent event) {
        AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
        // A persisted owner marker is always released before profile/dimension gates.
        resumeAi(animal, needs);
        if (!EcosystemParticipation.isEnabled(level.dimension(), true)) {
            EcosystemEvents.stopControllers(animal);
            return;
        }
        LevelRuntime runtime = runtime(level);
        WildlifeCandidate previous = runtime.wildlife.get(animal.getUUID());
        if (previous == null || !liveCandidate(level, previous.animal) || previous.cancelled()) {
            runtime.putCandidate(animal.getUUID(), new WildlifeCandidate(animal, event));
        }
        runtime.wildlifeScanRequested = true;
        if (!EcosystemParticipation.isEnabled(level)
                || SpeciesBehaviorProfileManager.profileFor(animal).isEmpty()) {
            EcosystemEvents.stopControllers(animal);
        }
    }

    /** Releases owned AI on tracking loss, retaining live entities until physical removal. */
    public void onEntityLeave(ServerLevel level, PathfinderMob animal) {
        LevelRuntime runtime = runtimes.get(level);
        if (runtime != null) {
            WildlifeCandidate candidate = runtime.wildlife.get(animal.getUUID());
            if (candidate != null && (candidate.animal == animal || !candidate.accepted)) {
                if (animal.isRemoved() || !animal.isAlive() || animal.level() != level) {
                    runtime.wildlife.remove(animal.getUUID());
                } else {
                    // Tracking callbacks prove insertion succeeded, even after lookup removal.
                    WildlifeCandidate accepted = new WildlifeCandidate(animal, null);
                    accepted.accepted = true;
                    runtime.putCandidate(animal.getUUID(), accepted);
                }
            }
        }
        resumeAi(animal, animal.getData(ModAttachments.ANIMAL_NEEDS));
    }

    /** Rediscovers loaded candidates once after startup/profile/config changes. */
    public void refreshLoadedWildlife(ServerLevel level) {
        LevelRuntime runtime = runtime(level);
        for (WildlifeCandidate candidate : runtime.wildlife.values()) {
            resumeAi(candidate.animal, candidate.animal.getData(ModAttachments.ANIMAL_NEEDS));
        }
        // Hidden accepted entities receive no Join when tracking resumes; keep their identity.
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof PathfinderMob animal) {
                onEntityJoin(level, animal);
            }
        }
        runtime.candidatesInitialized = true;
        runtime.lastCoverageRefresh = Long.MIN_VALUE;
        runtime.lastPlayerCellHash = Long.MIN_VALUE;
    }

    /** Returns a stable snapshot of relevant loaded wildlife for the transition owner. */
    public List<PathfinderMob> loadedWildlife(ServerLevel level) {
        LevelRuntime runtime = runtime(level);
        if (!runtime.candidatesInitialized) {
            refreshLoadedWildlife(level);
        }
        return visibleWildlife(level, runtime);
    }

    /** Reports retained physical identities without triggering discovery or cleanup. */
    int retainedCandidateCount(ServerLevel level) {
        LevelRuntime runtime = runtimes.get(level);
        return runtime == null ? 0 : runtime.wildlife.size();
    }

    private static void pruneCandidateLifecycles(ServerLevel level, LevelRuntime runtime) {
        int work = Math.min(CANDIDATE_LIFECYCLE_CHECKS_PER_TICK, runtime.candidateQueue.size());
        for (int checked = 0; checked < work; checked++) {
            UUID id = runtime.candidateQueue.removeFirst();
            WildlifeCandidate candidate = runtime.wildlife.get(id);
            if (candidate == null) {
                runtime.queuedCandidates.remove(id);
                continue;
            }
            if (!liveCandidate(level, candidate.animal) || candidate.cancelled()) {
                Entity canonical = level.getEntity(id);
                resumeAi(candidate.animal, candidate.animal.getData(ModAttachments.ANIMAL_NEEDS));
                EcosystemEvents.stopControllers(candidate.animal);
                if (canonical instanceof PathfinderMob animal && liveCandidate(level, animal)) {
                    WildlifeCandidate accepted = new WildlifeCandidate(animal, null);
                    accepted.accepted = true;
                    runtime.wildlife.put(id, accepted);
                } else {
                    runtime.wildlife.remove(id);
                    runtime.queuedCandidates.remove(id);
                    continue;
                }
            }
            runtime.candidateQueue.addLast(id);
        }
    }

    private static boolean liveCandidate(ServerLevel level, PathfinderMob animal) {
        return animal.isAlive() && !animal.isRemoved() && animal.level() == level;
    }

    /** Resolves only the in-memory entity lookup; hidden candidates never reach gameplay work. */
    private static List<PathfinderMob> visibleWildlife(ServerLevel level, LevelRuntime runtime) {
        List<PathfinderMob> visible = new ArrayList<>(runtime.wildlife.size());
        var iterator = runtime.wildlife.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            WildlifeCandidate candidate = entry.getValue();
            Entity canonical = level.getEntity(entry.getKey());
            if (canonical instanceof PathfinderMob animal && liveCandidate(level, animal)) {
                if (candidate.animal != animal) {
                    resumeAi(candidate.animal, candidate.animal.getData(ModAttachments.ANIMAL_NEEDS));
                    EcosystemEvents.stopControllers(candidate.animal);
                    candidate = new WildlifeCandidate(animal, null);
                    entry.setValue(candidate);
                }
                candidate.accepted = true;
                candidate.joinEvent = null;
                if (EcosystemParticipation.isEnabled(level)
                        && SpeciesBehaviorProfileManager.profileFor(animal).isPresent()) {
                    if (!animal.getData(ModAttachments.ANIMAL_NEEDS).controllerInstalled()) {
                        EcosystemEvents.installController(animal);
                    }
                    visible.add(animal);
                } else {
                    resumeAi(animal, animal.getData(ModAttachments.ANIMAL_NEEDS));
                    EcosystemEvents.stopControllers(animal);
                }
            } else if (canonical != null || !liveCandidate(level, candidate.animal)
                    || candidate.cancelled() || (!candidate.accepted && candidate.joinEvent == null)) {
                resumeAi(candidate.animal, candidate.animal.getData(ModAttachments.ANIMAL_NEEDS));
                EcosystemEvents.stopControllers(candidate.animal);
                iterator.remove();
            } else if (!EcosystemParticipation.isEnabled(level)
                    || SpeciesBehaviorProfileManager.profileFor(candidate.animal).isEmpty()) {
                // Eligibility may return while hidden; physical identity still belongs to this level.
                resumeAi(candidate.animal, candidate.animal.getData(ModAttachments.ANIMAL_NEEDS));
                EcosystemEvents.stopControllers(candidate.animal);
            }
        }
        return List.copyOf(visible);
    }

    /** Releases suspended AI immediately when a combat/interaction event protects this mob. */
    public void restoreAiIfProtected(ServerLevel level, PathfinderMob animal) {
        if (!EcosystemParticipation.isEnabled(level)
                || !DistantWildlifeManager.get().isSafeToAbstract(animal)) {
            resumeAi(animal, animal.getData(ModAttachments.ANIMAL_NEEDS));
        }
    }

    /** Restores manager-owned NoAI flags before normal server shutdown saves entities. */
    public void shutdown(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            resumeAll(level);
        }
        runtimes.clear();
    }

    private void tickLevel(ServerLevel level) {
        long started = System.nanoTime();
        long gameTime = level.getGameTime();
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        LevelRuntime runtime = runtime(level);
        beginMetricsTick(runtime, gameTime);
        if (!runtime.candidatesInitialized) {
            refreshLoadedWildlife(level);
        }
        // Hidden physical removal emits no second Leave. This bounded housekeeping
        // also runs while disabled, without profile queries or chunk access.
        pruneCandidateLifecycles(level, runtime);
        if (!settings.enabled() || !EcosystemParticipation.isEnabled(level)) {
            if (runtime.simulationEnabled || !runtime.enablementInitialized) {
                resumeAll(level);
                runtime.clearCells();
            }
            runtime.enablementInitialized = true;
            runtime.simulationEnabled = false;
            runtime.managerNanos += Math.max(0L, System.nanoTime() - started);
            publishMetrics(level, runtime, gameTime, 0);
            return;
        }
        runtime.enablementInitialized = true;
        runtime.simulationEnabled = true;
        boolean playersMovedCells = refreshPlayers(level, runtime, settings);

        boolean periodicCoverageDue = intervalElapsed(
                gameTime,
                runtime.lastCoverageRefresh,
                settings.regionalUpdateInterval()
        );
        boolean coverageDue = playersMovedCells || periodicCoverageDue;
        if (coverageDue) {
            scheduleKnownCells(level, runtime, settings);
            runtime.lastCoverageRefresh = gameTime;
        }
        int regionUpdates = processCellQueue(level, runtime, settings);
        if (playersMovedCells) {
            WildlifeScanMetrics scan = scanLoadedWildlife(level, runtime, settings);
            applyWildlifeScan(runtime, gameTime, scan);
        } else if (periodicCoverageDue) {
            runtime.wildlifeScanRequested = true;
        }
        runtime.managerNanos += Math.max(0L, System.nanoTime() - started);
        publishMetrics(level, runtime, gameTime, regionUpdates);
    }

    // Only cells with actual ecosystem relevance are queued; empty radius grids are never built.
    private void scheduleKnownCells(
            ServerLevel level,
            LevelRuntime runtime,
            EcosystemSimulationSettings settings
    ) {
        for (ServerPlayer player : relevantPlayers(level)) {
            queueLast(runtime, EcosystemCellKey.fromBlock(player.blockPosition(), settings.cellSize()).packed());
        }
        for (long key : List.copyOf(runtime.cells.keySet())) {
            queueLast(runtime, key);
        }
        if (EcosystemConfigAccess.distantWildlifeEnabled()) {
            long gameTime = level.getGameTime();
            for (DistantWildlifeGroup group : DistantWildlifeSavedData.get(level).groups()) {
                queueLast(runtime, EcosystemCellKey.fromBlock(
                        BlockPos.containing(group.positionAt(gameTime)), settings.cellSize()).packed());
            }
        }
    }

    private int processCellQueue(
            ServerLevel level,
            LevelRuntime runtime,
            EcosystemSimulationSettings settings
    ) {
        if (runtime.cellQueue.isEmpty()) {
            return 0;
        }
        int updated = 0;
        Set<Long> groupCells = abstractGroupCells(level, runtime, settings);
        while (updated < settings.maxRegionUpdatesPerTick() && !runtime.cellQueue.isEmpty()) {
            long packed = runtime.cellQueue.removeFirst();
            runtime.queuedCells.remove(packed);
            EcosystemCellKey key = EcosystemCellKey.fromPacked(packed);
            WildlifeSimulationLod simulationLevel = EcosystemZoneClassifier.classifyCell(
                    key, runtime.players, settings
            );
            if (simulationLevel == WildlifeSimulationLod.DORMANT && !groupCells.contains(packed)) {
                runtime.removeCell(packed);
            } else {
                runtime.putCell(packed, simulationLevel);
            }
            updated++;
        }
        return updated;
    }

    private WildlifeScanMetrics scanLoadedWildlife(
            ServerLevel level,
            LevelRuntime runtime,
            EcosystemSimulationSettings settings
    ) {
        long startedNanos = System.nanoTime();
        int scannedLoadedEntities = 0;
        int profiledWildlife = 0;
        int fullySimulated = 0;
        Set<Long> entityCells = new HashSet<>();
        for (PathfinderMob animal : visibleWildlife(level, runtime)) {
            scannedLoadedEntities++;
            AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
            profiledWildlife++;
            EcosystemCellKey key = EcosystemCellKey.fromBlock(animal.blockPosition(), settings.cellSize());
            entityCells.add(key.packed());
            WildlifeSimulationLod simulationLevel = EcosystemZoneClassifier.classifyCell(
                    key, runtime.players, settings
            );
            runtime.putCell(key.packed(), simulationLevel);
            needs.setSimulationLod(simulationLevel);
            if (simulationLevel == WildlifeSimulationLod.ACTIVE) {
                fullySimulated++;
            }

            if (EcosystemConfigAccess.distantWildlifeEnabled()
                    && (simulationLevel == WildlifeSimulationLod.DISTANT
                    || simulationLevel == WildlifeSimulationLod.DORMANT)) {
                if (DistantWildlifeManager.get().isSafeToAbstract(animal)) {
                    suspendAi(animal, needs);
                } else {
                    resumeAi(animal, needs);
                    fullySimulated++;
                }
            } else {
                resumeAi(animal, needs);
            }
        }
        runtime.lastFullySimulatedEntities = fullySimulated;
        for (long key : entityCells) {
            queueLast(runtime, key);
        }
        return new WildlifeScanMetrics(
                scannedLoadedEntities,
                profiledWildlife,
                Math.max(0L, System.nanoTime() - startedNanos)
        );
    }

    private static void applyWildlifeScan(
            LevelRuntime runtime,
            long gameTime,
            WildlifeScanMetrics scan
    ) {
        runtime.lastWildlifeScanTick = gameTime;
        runtime.lastScannedLoadedEntities = scan.scannedLoadedEntities();
        runtime.lastProfiledWildlife = scan.profiledWildlife();
        runtime.lastWildlifeScanNanos = scan.elapsedNanos();
        runtime.wildlifeScanRequested = false;
    }

    private static void suspendAi(PathfinderMob animal, AnimalNeedsState needs) {
        if (needs.simulationAiSuspended()) {
            return;
        }
        needs.idle();
        animal.getNavigation().stop();
        needs.suspendAiForSimulation(animal.isNoAi());
        animal.setNoAi(true);
    }

    private static void resumeAi(PathfinderMob animal, AnimalNeedsState needs) {
        if (needs.simulationAiSuspended()) {
            animal.setNoAi(needs.resumeAiFromSimulation());
            needs.scheduleEvaluation(animal.level().getGameTime() + 1L);
        }
    }

    private void resumeAll(ServerLevel level) {
        LevelRuntime runtime = runtime(level);
        if (!runtime.candidatesInitialized) {
            refreshLoadedWildlife(level);
        }
        for (WildlifeCandidate candidate : runtime.wildlife.values()) {
            PathfinderMob animal = candidate.animal;
            resumeAi(animal, animal.getData(ModAttachments.ANIMAL_NEEDS));
            if (!EcosystemParticipation.isEnabled(level)) {
                EcosystemEvents.stopControllers(animal);
            }
        }
    }

    private boolean refreshPlayers(
            ServerLevel level,
            LevelRuntime runtime,
            EcosystemSimulationSettings settings
    ) {
        List<ServerPlayer> relevant = relevantPlayers(level);
        List<EcosystemZoneClassifier.PlayerPoint> points = new ArrayList<>(relevant.size());
        long hash = 1L;
        for (ServerPlayer player : relevant) {
            EcosystemCellKey key = EcosystemCellKey.fromBlock(player.blockPosition(), settings.cellSize());
            points.add(new EcosystemZoneClassifier.PlayerPoint(player.getX(), player.getZ()));
            hash = 31L * hash + player.getUUID().hashCode();
            hash = 31L * hash + Long.hashCode(key.packed());
        }
        boolean changed = hash != runtime.lastPlayerCellHash || points.size() != runtime.players.size();
        runtime.players = List.copyOf(points);
        runtime.lastPlayerCellHash = hash;
        runtime.playersInitialized = true;
        return changed;
    }

    private static List<ServerPlayer> relevantPlayers(ServerLevel level) {
        return level.players().stream()
                .filter(ServerPlayer::isAlive)
                .filter(player -> !player.isSpectator())
                .toList();
    }

    private static Set<Long> abstractGroupCells(
            ServerLevel level,
            LevelRuntime runtime,
            EcosystemSimulationSettings settings
    ) {
        if (!EcosystemConfigAccess.distantWildlifeEnabled()) {
            return Set.of();
        }
        long gameTime = level.getGameTime();
        DistantWildlifeSavedData data = DistantWildlifeSavedData.get(level);
        if (runtime.abstractGroupRevision == data.revision()
                && !intervalElapsed(gameTime, runtime.lastGroupCellRefresh, settings.regionalUpdateInterval())) {
            return runtime.abstractGroupCells;
        }
        Set<Long> cells = new HashSet<>();
        for (DistantWildlifeGroup group : data.groups()) {
            cells.add(EcosystemCellKey.fromBlock(
                    BlockPos.containing(group.positionAt(gameTime)), settings.cellSize()).packed());
        }
        runtime.abstractGroupCells = Set.copyOf(cells);
        runtime.abstractGroupRevision = data.revision();
        runtime.lastGroupCellRefresh = gameTime;
        return runtime.abstractGroupCells;
    }

    private void publishMetrics(
            ServerLevel level,
            LevelRuntime runtime,
            long gameTime,
            int regionUpdates
    ) {
        int abstractPopulation = EcosystemParticipation.isEnabled(level) && EcosystemConfigAccess.distantWildlifeEnabled()
                ? DistantWildlifeSavedData.get(level).representedAnimals()
                : 0;
        runtime.metrics = new EcosystemSimulationMetrics.Snapshot(
                gameTime,
                runtime.cellCounts[WildlifeSimulationLod.ACTIVE.ordinal()],
                runtime.cellCounts[WildlifeSimulationLod.NEAR.ordinal()],
                runtime.cellCounts[WildlifeSimulationLod.DISTANT.ordinal()],
                runtime.cellCounts[WildlifeSimulationLod.DORMANT.ordinal()],
                runtime.lastFullySimulatedEntities,
                abstractPopulation,
                regionUpdates,
                runtime.cellQueue.size(),
                runtime.lastWildlifeScanTick,
                runtime.lastScannedLoadedEntities,
                runtime.lastProfiledWildlife,
                runtime.lastWildlifeScanNanos,
                runtime.entityEvaluationNanos + runtime.externalNanos + runtime.managerNanos
        );
    }

    private static void beginMetricsTick(LevelRuntime runtime, long gameTime) {
        if (runtime.metricsTick != gameTime) {
            runtime.metricsTick = gameTime;
            runtime.entityEvaluationNanos = 0L;
            runtime.externalNanos = 0L;
            runtime.managerNanos = 0L;
        }
    }

    private static boolean intervalElapsed(long currentTick, long lastTick, int intervalTicks) {
        return lastTick == Long.MIN_VALUE
                || currentTick < lastTick
                || currentTick - lastTick >= Math.max(1, intervalTicks);
    }

    private static void queueFirst(LevelRuntime runtime, long key) {
        if (runtime.queuedCells.add(key)) {
            runtime.cellQueue.addFirst(key);
        }
    }

    private static void queueLast(LevelRuntime runtime, long key) {
        if (runtime.queuedCells.add(key)) {
            runtime.cellQueue.addLast(key);
        }
    }

    private LevelRuntime runtime(ServerLevel level) {
        return runtimes.computeIfAbsent(level, ignored -> new LevelRuntime());
    }

    /** Measured work from the periodic full loaded-entity ecosystem pass. */
    private record WildlifeScanMetrics(
            int scannedLoadedEntities,
            int profiledWildlife,
            long elapsedNanos
    ) {
    }

    /** Isolates the config dependency used by static scheduling helpers. */
    private static final class EcosystemConfigAccess {
        private static boolean distantWildlifeEnabled() {
            return com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig
                    .distantWildlifeSettings().enabled();
        }
    }

    private static final class LevelRuntime {
        private final Map<Long, WildlifeSimulationLod> cells = new HashMap<>();
        private final int[] cellCounts = new int[WildlifeSimulationLod.values().length];
        private final Map<UUID, WildlifeCandidate> wildlife = new HashMap<>();
        private final Deque<UUID> candidateQueue = new ArrayDeque<>();
        private final Set<UUID> queuedCandidates = new HashSet<>();
        private final Deque<Long> cellQueue = new ArrayDeque<>();
        private final Set<Long> queuedCells = new HashSet<>();
        private List<EcosystemZoneClassifier.PlayerPoint> players = List.of();
        private long lastCoverageRefresh = Long.MIN_VALUE;
        private long lastPlayerCellHash = Long.MIN_VALUE;
        private boolean playersInitialized;
        private boolean candidatesInitialized;
        private boolean enablementInitialized;
        private boolean simulationEnabled;
        private Set<Long> abstractGroupCells = Set.of();
        private long abstractGroupRevision = Long.MIN_VALUE;
        private long lastGroupCellRefresh = Long.MIN_VALUE;
        private long metricsTick = Long.MIN_VALUE;
        private long entityEvaluationNanos;
        private long externalNanos;
        private long managerNanos;
        private long lastWildlifeScanTick;
        private int lastScannedLoadedEntities;
        private int lastProfiledWildlife;
        private long lastWildlifeScanNanos;
        private int lastFullySimulatedEntities;
        private boolean wildlifeScanRequested;
        private EcosystemSimulationMetrics.Snapshot metrics = EcosystemSimulationMetrics.Snapshot.EMPTY;

        private void putCandidate(UUID id, WildlifeCandidate candidate) {
            wildlife.put(id, candidate);
            if (queuedCandidates.add(id)) {
                candidateQueue.addLast(id);
            }
        }

        private void putCell(long key, WildlifeSimulationLod simulationLevel) {
            WildlifeSimulationLod previous = cells.put(key, simulationLevel);
            if (previous != simulationLevel) {
                if (previous != null) {
                    cellCounts[previous.ordinal()]--;
                }
                cellCounts[simulationLevel.ordinal()]++;
            }
        }

        private void removeCell(long key) {
            WildlifeSimulationLod previous = cells.remove(key);
            if (previous != null) {
                cellCounts[previous.ordinal()]--;
            }
        }

        private void clearCells() {
            cells.clear();
            java.util.Arrays.fill(cellCounts, 0);
            abstractGroupCells = Set.of();
            abstractGroupRevision = Long.MIN_VALUE;
            lastGroupCellRefresh = Long.MIN_VALUE;
            cellQueue.clear();
            queuedCells.clear();
            lastWildlifeScanTick = 0L;
            lastScannedLoadedEntities = 0;
            lastProfiledWildlife = 0;
            lastWildlifeScanNanos = 0L;
            lastFullySimulatedEntities = 0;
            wildlifeScanRequested = false;
        }
    }

    private static final class WildlifeCandidate {
        private final PathfinderMob animal;
        private EntityJoinLevelEvent joinEvent;
        private boolean accepted;

        private WildlifeCandidate(PathfinderMob animal, EntityJoinLevelEvent joinEvent) {
            this.animal = animal;
            this.joinEvent = joinEvent;
        }

        private boolean cancelled() {
            return joinEvent != null && joinEvent.isCanceled();
        }
    }
}
