package com.thunder.wildernessodysseyapi.simulation.integration;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import com.thunder.wildernessodysseyapi.dataengine.async.AsyncDataTask;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeSavedData;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemCellKey;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationContext;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationRegionCollector;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationServices;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationSystem;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * First concrete Simulation Engine participant: coarse distant-wildlife ecology.
 *
 * <p>The participant discovers only regions in the bounded
 * {@link DistantWildlifeSavedData} ledger. Pure analytical work runs through the
 * existing Data Engine, then the ledger revalidates and commits on the logical
 * server thread. No entities, chunks, or live owner state reach a worker.</p>
 */
public final class PopulationEcologySimulationSystem implements SimulationSystem {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "population_ecology"
    );

    private static final int DISTURBANCE_RADIUS = 64;
    private static final long COLLECTION_RETRY_TICKS = 1_200L;
    private static final long ASYNC_TIMEOUT_TICKS = 1_200L;
    private static final PopulationEcologySimulationSystem INSTANCE = new PopulationEcologySimulationSystem();

    private static boolean bootstrapped;

    private final Map<ResourceLocation, Long> nextCollectionTicks = new HashMap<>();
    private final Map<ResourceLocation, Long> lastCollectionTicks = new HashMap<>();
    private final Map<PopulationRegionKey, PendingSubmission> inFlight = new HashMap<>();
    private final Map<ResourceLocation, MutableDiagnostics> diagnostics = new HashMap<>();

    private MinecraftServer server;
    private long generation;

    private PopulationEcologySimulationSystem() {
    }

    /** Registers the singleton participant once during common mod setup. */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        SimulationServices.register(INSTANCE);
        bootstrapped = true;
    }

    /** Returns the registered participant for server diagnostics. */
    public static PopulationEcologySimulationSystem get() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return EcosystemConfig.populationEcologySettings().enabled();
    }

    @Override
    public void collectRegions(MinecraftServer currentServer, SimulationRegionCollector collector) {
        if (server != currentServer) {
            return;
        }
        EcosystemConfig.PopulationEcologySettings settings = EcosystemConfig.populationEcologySettings();
        for (ServerLevel level : currentServer.getAllLevels()) {
            ResourceLocation dimension = level.dimension().location();
            long gameTime = level.getGameTime();
            long nextCollection = nextCollectionTicks.getOrDefault(dimension, Long.MIN_VALUE);
            long lastCollection = lastCollectionTicks.getOrDefault(dimension, Long.MIN_VALUE);
            if (lastCollection != Long.MIN_VALUE
                    && gameTime >= lastCollection
                    && nextCollection != Long.MIN_VALUE
                    && gameTime < nextCollection) {
                continue;
            }
            expireStaleSubmissions(dimension, gameTime);
            long earliestFuture = saturatingAdd(gameTime, settings.updateIntervalTicks());
            boolean dueFound = false;
            DistantWildlifeSavedData data = DistantWildlifeSavedData.get(level);
            for (DistantWildlifeGroup group : data.groups()) {
                long dueAt = saturatingAdd(group.populationReferenceGameTime(), settings.updateIntervalTicks());
                if (gameTime >= group.populationReferenceGameTime() && gameTime < dueAt) {
                    earliestFuture = Math.min(earliestFuture, dueAt);
                    continue;
                }
                BlockPos position = BlockPos.containing(group.positionAt(gameTime));
                SimulationRegion region = EcosystemSimulationBridge.regionAt(level, position);
                if (inFlight.containsKey(PopulationRegionKey.from(region))) {
                    dueFound = true;
                    continue;
                }
                MutableDiagnostics levelDiagnostics = mutableDiagnostics(level);
                if (collector.request(level, position)) {
                    levelDiagnostics.regionRequests++;
                } else {
                    levelDiagnostics.regionRequestRejections++;
                }
                dueFound = true;
            }
            nextCollectionTicks.put(
                    dimension,
                    dueFound ? Math.min(earliestFuture, saturatingAdd(gameTime, COLLECTION_RETRY_TICKS)) : earliestFuture
            );
            lastCollectionTicks.put(dimension, gameTime);
        }
    }

    @Override
    public boolean shouldUpdate(SimulationContext context) {
        Optional<EcosystemRegionSnapshot> ecosystem = context.ecosystem();
        if (ecosystem.isEmpty() || ecosystem.get().groupCount() == 0) {
            return false;
        }
        PopulationRegionKey key = PopulationRegionKey.from(context.region());
        PendingSubmission pending = inFlight.get(key);
        if (pending != null && !isExpired(pending, context.gameTime())) {
            return false;
        }
        if (pending != null) {
            inFlight.remove(key);
            mutableDiagnostics(context.level()).timedOutSubmissions++;
        }
        return populationDue(context, ecosystem.get().key());
    }

    @Override
    public void update(SimulationContext context) {
        EcosystemRegionSnapshot ecosystem = context.ecosystem().orElse(null);
        if (ecosystem == null) {
            return;
        }
        EcosystemConfig.PopulationEcologySettings settings = EcosystemConfig.populationEcologySettings();
        DistantWildlifeSavedData data = DistantWildlifeSavedData.get(context.level());
        List<DistantWildlifeGroup> groups = data.groupsInRegion(
                ecosystem.key(),
                context.region().cellSize(),
                context.gameTime()
        );
        if (groups.isEmpty()) {
            return;
        }

        double disturbance = currentDisturbance(context.level(), context.region().anchor(context.level()), context.gameTime());
        AbstractEcosystemModel.Environment conditions = PopulationEcologyModel.conditions(
                ecosystem,
                context.environment(),
                disturbance,
                settings.regionalCarryingCapacity()
        );
        PopulationTaskInput input = new PopulationTaskInput(
                groups,
                conditions,
                context.gameTime(),
                settings.updateIntervalTicks()
        );
        PopulationRegionKey key = PopulationRegionKey.from(context.region());
        PendingSubmission submission = new PendingSubmission(generation, context.gameTime());

        boolean accepted = DataEngine.get().runAsync(
                ID,
                "population_" + context.region().cellX() + "_" + context.region().cellZ(),
                UpdatePriority.BACKGROUND,
                true,
                new PopulationTask(input, key, submission, settings)
        );
        MutableDiagnostics levelDiagnostics = mutableDiagnostics(context.level());
        if (accepted) {
            inFlight.put(key, submission);
            levelDiagnostics.submittedBatches++;
        } else {
            levelDiagnostics.rejectedBatches++;
            nextCollectionTicks.put(context.level().dimension().location(), context.gameTime());
        }
    }

    @Override
    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        generation++;
        nextCollectionTicks.clear();
        lastCollectionTicks.clear();
        inFlight.clear();
        diagnostics.clear();
        DataEngine.get().registerSystem(DataSystemRegistration.builder(ID)
                .priority(UpdatePriority.BACKGROUND)
                .build());
    }

    @Override
    public void onConfigurationReload() {
        generation++;
        nextCollectionTicks.clear();
        lastCollectionTicks.clear();
        inFlight.clear();
    }

    @Override
    public void onLevelUnload(ResourceLocation dimension) {
        nextCollectionTicks.remove(dimension);
        lastCollectionTicks.remove(dimension);
        diagnostics.remove(dimension);
        inFlight.keySet().removeIf(key -> key.dimension().equals(dimension));
    }

    @Override
    public void onServerStopping() {
        server = null;
        generation++;
        nextCollectionTicks.clear();
        lastCollectionTicks.clear();
        inFlight.clear();
        diagnostics.clear();
    }

    /** Returns a point-in-time per-dimension diagnostic snapshot. */
    public Diagnostics diagnostics(ServerLevel level) {
        MutableDiagnostics current = diagnostics.get(level.dimension().location());
        EcosystemConfig.PopulationEcologySettings settings = EcosystemConfig.populationEcologySettings();
        int levelInFlight = (int) inFlight.keySet().stream()
                .filter(key -> key.dimension().equals(level.dimension().location()))
                .count();
        return current == null
                ? Diagnostics.empty(settings, levelInFlight)
                : current.snapshot(settings, levelInFlight);
    }

    private void applyResult(
            PopulationRegionKey key,
            PendingSubmission submission,
            PopulationEcologyModel.Calculation calculation,
            EcosystemConfig.PopulationEcologySettings settings
    ) {
        ServerLevel level = resolveLevel(key.dimension());
        if (level == null) {
            inFlight.remove(key, submission);
            return;
        }
        long started = System.nanoTime();
        try {
            DistantWildlifeSavedData.PopulationApplyResult applied = DistantWildlifeSavedData.get(level)
                    .applyPopulationUpdates(calculation.updates(), settings.maximumRepresentedAnimals());
            MutableDiagnostics levelDiagnostics = mutableDiagnostics(level);
            levelDiagnostics.appliedBatches++;
            levelDiagnostics.appliedGroups += applied.appliedGroups();
            levelDiagnostics.staleGroups += applied.staleGroups();
            levelDiagnostics.animalsAdded += applied.animalsAdded();
            levelDiagnostics.animalsRemoved += applied.animalsRemoved();
            levelDiagnostics.lastApplyGameTime = level.getGameTime();
            if (applied.populationChanged()) {
                DistantWildlifeManager.get().markPopulationChanged(level);
            }
        } finally {
            inFlight.remove(key, submission);
            EcosystemSimulationManager.get().recordExternalWork(
                    level,
                    level.getGameTime(),
                    Math.max(0L, System.nanoTime() - started)
            );
        }
    }

    private boolean isTaskCurrent(
            PopulationRegionKey key,
            PendingSubmission submission,
            PopulationEcologyModel.Calculation result
    ) {
        ServerLevel level = resolveLevel(key.dimension());
        return level != null
                && generation == submission.generation()
                && inFlight.get(key) == submission
                && isEnabled()
                && DistantWildlifeSavedData.get(level).hasCurrentPopulationUpdate(result.updates());
    }

    private void discardTask(PopulationRegionKey key, PendingSubmission submission) {
        inFlight.remove(key, submission);
        ServerLevel level = resolveLevel(key.dimension());
        if (level != null) {
            mutableDiagnostics(level).discardedBatches++;
        }
    }

    private ServerLevel resolveLevel(ResourceLocation dimension) {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return currentServer.getLevel(key);
    }

    private boolean populationDue(SimulationContext context, EcosystemCellKey region) {
        int interval = EcosystemConfig.populationEcologySettings().updateIntervalTicks();
        for (DistantWildlifeGroup group : DistantWildlifeSavedData.get(context.level()).groupsInRegion(
                region,
                context.region().cellSize(),
                context.gameTime()
        )) {
            if (context.gameTime() < group.populationReferenceGameTime()
                    || context.gameTime() - group.populationReferenceGameTime() >= interval) {
                return true;
            }
        }
        return false;
    }

    private void expireStaleSubmissions(ResourceLocation dimension, long gameTime) {
        Iterator<Map.Entry<PopulationRegionKey, PendingSubmission>> iterator = inFlight.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PopulationRegionKey, PendingSubmission> entry = iterator.next();
            if (entry.getKey().dimension().equals(dimension) && isExpired(entry.getValue(), gameTime)) {
                iterator.remove();
                diagnostics.computeIfAbsent(dimension, ignored -> new MutableDiagnostics()).timedOutSubmissions++;
            }
        }
    }

    private boolean isExpired(PendingSubmission submission, long gameTime) {
        return submission.generation() != generation
                || gameTime < submission.submittedGameTime()
                || gameTime - submission.submittedGameTime() >= ASYNC_TIMEOUT_TICKS;
    }

    private MutableDiagnostics mutableDiagnostics(ServerLevel level) {
        return diagnostics.computeIfAbsent(level.dimension().location(), ignored -> new MutableDiagnostics());
    }

    private static double currentDisturbance(ServerLevel level, BlockPos position, long gameTime) {
        double immediate = EcosystemServices.disturbances().nearest(
                        level,
                        position,
                        DISTURBANCE_RADIUS,
                        gameTime
                )
                .map(EnvironmentalContext.Disturbance::intensity)
                .orElse(0.0);
        double remembered = EnvironmentalMemoryManager.getMemory(level, position)
                .map(memory -> memory.strongestActivity())
                .orElse(0.0);
        return Math.max(immediate, remembered);
    }

    private static long saturatingAdd(long value, long increment) {
        long safeIncrement = Math.max(0L, increment);
        return value > Long.MAX_VALUE - safeIncrement ? Long.MAX_VALUE : value + safeIncrement;
    }

    private record PopulationTaskInput(
            List<DistantWildlifeGroup> groups,
            AbstractEcosystemModel.Environment conditions,
            long gameTime,
            long minimumElapsedTicks
    ) {
        private PopulationTaskInput {
            groups = List.copyOf(groups);
        }
    }

    private record PopulationRegionKey(
            ResourceLocation dimension,
            int cellX,
            int cellZ,
            int cellSize
    ) {
        private static PopulationRegionKey from(SimulationRegion region) {
            return new PopulationRegionKey(
                    region.dimension(),
                    region.cellX(),
                    region.cellZ(),
                    region.cellSize()
            );
        }
    }

    private record PendingSubmission(long generation, long submittedGameTime) {
    }

    /** Worker object containing only copied records, primitive settings, and stable IDs. */
    private record PopulationTask(
            PopulationTaskInput input,
            PopulationRegionKey key,
            PendingSubmission submission,
            EcosystemConfig.PopulationEcologySettings settings
    ) implements AsyncDataTask<PopulationEcologyModel.Calculation> {
        @Override
        public PopulationEcologyModel.Calculation compute() {
            return PopulationEcologyModel.calculate(
                    input.groups(),
                    input.conditions(),
                    input.gameTime(),
                    input.minimumElapsedTicks()
            );
        }

        @Override
        public boolean isStillValid(PopulationEcologyModel.Calculation result) {
            return INSTANCE.isTaskCurrent(key, submission, result);
        }

        @Override
        public void apply(PopulationEcologyModel.Calculation result) {
            INSTANCE.applyResult(key, submission, result, settings);
        }

        @Override
        public void onDiscarded(PopulationEcologyModel.Calculation result) {
            INSTANCE.discardTask(key, submission);
        }
    }

    /** Operator-facing bounded counters for one dimension. */
    public record Diagnostics(
            boolean enabled,
            int updateIntervalTicks,
            int regionalCarryingCapacity,
            long regionRequests,
            long regionRequestRejections,
            long submittedBatches,
            long rejectedBatches,
            long appliedBatches,
            long discardedBatches,
            long timedOutSubmissions,
            long appliedGroups,
            long staleGroups,
            long animalsAdded,
            long animalsRemoved,
            int inFlight,
            long lastApplyGameTime
    ) {
        private static Diagnostics empty(EcosystemConfig.PopulationEcologySettings settings, int inFlight) {
            return new Diagnostics(
                    settings.enabled(),
                    settings.updateIntervalTicks(),
                    settings.regionalCarryingCapacity(),
                    0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    inFlight,
                    0L
            );
        }
    }

    private static final class MutableDiagnostics {
        private long regionRequests;
        private long regionRequestRejections;
        private long submittedBatches;
        private long rejectedBatches;
        private long appliedBatches;
        private long discardedBatches;
        private long timedOutSubmissions;
        private long appliedGroups;
        private long staleGroups;
        private long animalsAdded;
        private long animalsRemoved;
        private long lastApplyGameTime;

        private Diagnostics snapshot(EcosystemConfig.PopulationEcologySettings settings, int inFlight) {
            return new Diagnostics(
                    settings.enabled(),
                    settings.updateIntervalTicks(),
                    settings.regionalCarryingCapacity(),
                    regionRequests,
                    regionRequestRejections,
                    submittedBatches,
                    rejectedBatches,
                    appliedBatches,
                    discardedBatches,
                    timedOutSubmissions,
                    appliedGroups,
                    staleGroups,
                    animalsAdded,
                    animalsRemoved,
                    inFlight,
                    lastApplyGameTime
            );
        }
    }
}
