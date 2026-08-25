package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeSavedData;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

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
     * Runs periodic loaded-wildlife scans from the bounded Data Engine path.
     *
     * <p>Only already-loaded entities are inspected. A player entering a new
     * ecosystem cell still performs the immediate scan in {@link #tick} so
     * manager-owned AI suspension cannot delay visible wildlife recovery.</p>
     */
    public void runOptionalMaintenance(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelRuntime runtime = runtimes.get(level);
            if (runtime == null || !runtime.wildlifeScanRequested) {
                continue;
            }
            EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
            if (!settings.enabled()) {
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
        if (!settings.enabled()) {
            return WildlifeSimulationLod.ACTIVE;
        }
        LevelRuntime runtime = runtime(level);
        if (!runtime.playersInitialized) {
            refreshPlayers(level, runtime, settings);
        }
        EcosystemCellKey key = EcosystemCellKey.fromBlock(position, settings.cellSize());
        return runtime.cells.computeIfAbsent(
                key.packed(),
                ignored -> EcosystemZoneClassifier.classifyCell(key, runtime.players, settings)
        );
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
        if (!settings.enabled()) {
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
        return getSimulationLevel(level, position) == WildlifeSimulationLod.ACTIVE;
    }

    /** Returns exact horizontal distance to the nearest alive, non-spectating player. */
    public double getNearestPlayerDistance(ServerLevel level, BlockPos position) {
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
        EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
        LevelRuntime runtime = runtime(level);
        queueFirst(runtime, EcosystemCellKey.fromBlock(position, settings.cellSize()).packed());
    }

    /** Returns an immutable aggregate of the abstract groups currently occupying one cell. */
    public Optional<EcosystemRegionSnapshot> getRegionSnapshot(ServerLevel level, BlockPos position) {
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
        LevelRuntime runtime = runtime(level);
        beginMetricsTick(runtime, gameTime);
        runtime.entityEvaluationNanos += Math.max(0L, elapsedNanos);
    }

    /** Adds work performed by the delegated distant-group transition owner. */
    public void recordExternalWork(ServerLevel level, long gameTime, long elapsedNanos) {
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
            if (!EcosystemSimulationSettings.fromConfig().enabled()) {
                resumeAll(level);
                runtime.clearCells();
            }
        }
    }

    /** Releases only transient caches for an unloading dimension. */
    public void unload(ServerLevel level) {
        runtimes.remove(level);
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
        boolean playersMovedCells = refreshPlayers(level, runtime, settings);
        if (!settings.enabled()) {
            resumeAll(level);
            runtime.clearCells();
            runtime.managerNanos += Math.max(0L, System.nanoTime() - started);
            publishMetrics(level, runtime, gameTime, 0);
            return;
        }

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
        int updated = 0;
        Set<Long> groupCells = abstractGroupCells(level, settings);
        while (updated < settings.maxRegionUpdatesPerTick() && !runtime.cellQueue.isEmpty()) {
            long packed = runtime.cellQueue.removeFirst();
            runtime.queuedCells.remove(packed);
            EcosystemCellKey key = EcosystemCellKey.fromPacked(packed);
            WildlifeSimulationLod simulationLevel = EcosystemZoneClassifier.classifyCell(
                    key, runtime.players, settings
            );
            if (simulationLevel == WildlifeSimulationLod.DORMANT && !groupCells.contains(packed)) {
                runtime.cells.remove(packed);
            } else {
                runtime.cells.put(packed, simulationLevel);
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
        for (Entity entity : level.getAllEntities()) {
            scannedLoadedEntities++;
            if (!(entity instanceof PathfinderMob animal)
                    || SpeciesBehaviorProfileManager.profileFor(animal).isEmpty()) {
                continue;
            }
            profiledWildlife++;
            EcosystemCellKey key = EcosystemCellKey.fromBlock(animal.blockPosition(), settings.cellSize());
            entityCells.add(key.packed());
            WildlifeSimulationLod simulationLevel = EcosystemZoneClassifier.classifyCell(
                    key, runtime.players, settings
            );
            runtime.cells.put(key.packed(), simulationLevel);
            AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
            needs.setSimulationLod(simulationLevel);
            if (simulationLevel == WildlifeSimulationLod.ACTIVE) {
                fullySimulated++;
            }

            if (EcosystemConfigAccess.distantWildlifeEnabled()
                    && (simulationLevel == WildlifeSimulationLod.DISTANT
                    || simulationLevel == WildlifeSimulationLod.DORMANT)) {
                if (EcosystemEntitySafety.mayAbstract(animal)) {
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

    private static void resumeAll(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof PathfinderMob animal) {
                resumeAi(animal, animal.getData(ModAttachments.ANIMAL_NEEDS));
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
            EcosystemSimulationSettings settings
    ) {
        if (!EcosystemConfigAccess.distantWildlifeEnabled()) {
            return Set.of();
        }
        long gameTime = level.getGameTime();
        Set<Long> cells = new HashSet<>();
        for (DistantWildlifeGroup group : DistantWildlifeSavedData.get(level).groups()) {
            cells.add(EcosystemCellKey.fromBlock(
                    BlockPos.containing(group.positionAt(gameTime)), settings.cellSize()).packed());
        }
        return cells;
    }

    private void publishMetrics(
            ServerLevel level,
            LevelRuntime runtime,
            long gameTime,
            int regionUpdates
    ) {
        int active = 0;
        int near = 0;
        int distant = 0;
        int dormant = 0;
        for (WildlifeSimulationLod simulationLevel : runtime.cells.values()) {
            switch (simulationLevel) {
                case ACTIVE -> active++;
                case NEAR -> near++;
                case DISTANT -> distant++;
                case DORMANT -> dormant++;
            }
        }
        int abstractPopulation = EcosystemConfigAccess.distantWildlifeEnabled()
                ? DistantWildlifeSavedData.get(level).representedAnimals()
                : 0;
        runtime.metrics = new EcosystemSimulationMetrics.Snapshot(
                gameTime,
                active,
                near,
                distant,
                dormant,
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
        private final Deque<Long> cellQueue = new ArrayDeque<>();
        private final Set<Long> queuedCells = new HashSet<>();
        private List<EcosystemZoneClassifier.PlayerPoint> players = List.of();
        private long lastCoverageRefresh = Long.MIN_VALUE;
        private long lastPlayerCellHash = Long.MIN_VALUE;
        private boolean playersInitialized;
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

        private void clearCells() {
            cells.clear();
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
}
