package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Time-slices player-relevant loaded watershed cells on the server thread.
 *
 * <p>The queue caches deterministic downstream links from packed terrain state,
 * samples the public weather query, and routes runoff only when the downstream
 * chunk is already loaded. No path in this manager loads a chunk, scans a river,
 * or mutates generated fluid columns.</p>
 */
public final class WatershedSimulationManager {

    private static final Map<ServerLevel, RuntimeState> RUNTIMES = new ConcurrentHashMap<>();
    private static final int DEBUG_LOG_INTERVAL_TICKS = 1_200;

    private WatershedSimulationManager() {
    }

    /** Initializes metadata for one chunk after normal promotion/loading finishes. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (WaterSimulationConfig.watershedSimulationEnabled()) {
            WatershedSavedData data = WatershedSavedData.get(level);
            data.getOrCreate(level, chunk);
            reconcileLoadedConnections(level, data, chunk.getPos().toLong());
        }
    }

    /** Advances a bounded queue and performs exact flood recession every server tick. */
    public static void tickLevel(ServerLevel level) {
        long started = System.nanoTime();
        WatershedSavedData data = WatershedSavedData.get(level);
        WatershedBasinSavedData basins = WatershedBasinSavedData.get(level);
        RuntimeState runtime = RUNTIMES.computeIfAbsent(level, ignored -> new RuntimeState());
        int initialized = 0;
        int processed = 0;
        int placements = 0;
        int floodPlacements = 0;
        int standingWaterPlacements = 0;

        if (WaterSimulationConfig.watershedSimulationEnabled()) {
            long gameTime = level.getGameTime();
            if (runtime.queue.isEmpty()
                    && Math.floorMod(gameTime, WaterSimulationConfig.watershedUpdateIntervalTicks()) == 0L) {
                initialized = enqueuePlayerRelevant(level, data, runtime);
            }

            int placementBudget = WaterSimulationConfig.maximumFloodPlacementsPerTick();
            int standingWaterBudget = WaterSimulationConfig.surfaceWaterMaximumPlacementsPerTick();
            int processBudget = WaterSimulationConfig.watershedChunksPerTick();
            boolean weatherEnabled = WaterSimulationConfig.weatherWaterCouplingEnabled()
                    && WeatherConfig.dimensionEnabled(level.dimension());
            while (processed < processBudget) {
                Long chunkKey = runtime.poll();
                if (chunkKey == null) {
                    break;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        ChunkPos.getX(chunkKey),
                        ChunkPos.getZ(chunkKey)
                );
                if (chunk == null) {
                    continue;
                }
                WatershedChunkState state = data.getOrCreate(level, chunk);
                WatershedConditions previous = state.conditions(basins.resolve(state.localBasinId()));
                Downstream downstream = downstream(level, data, chunkKey, previous.downstreamDirection());
                if (downstream.state != null) {
                    long canonical = basins.union(state.localBasinId(), downstream.state.localBasinId());
                    previous = state.conditions(canonical);
                }
                WeatherSample weather = weatherEnabled
                        ? WeatherServices.query().sample(level, samplePosition(level, chunk, state))
                        : WeatherSample.CLEAR;
                WatershedSimulationModel.Result result = WatershedSimulationModel.advance(
                        new WatershedSimulationModel.Input(
                                previous,
                                weather,
                                0.0f,
                                WaterSimulationConfig.watershedRainfallAccumulationRate(),
                                WaterSimulationConfig.watershedDrainageRate(),
                                WaterSimulationConfig.watershedMaximumWaterLevelOffset(),
                                WaterSimulationConfig.watershedFloodThreshold(),
                                weatherEnabled,
                                downstream.state != null,
                                WaterSimulationConfig.watershedSedimentEffectsEnabled(),
                                WaterSimulationConfig.watershedDebrisEffectsEnabled(),
                                WaterSimulationConfig.watershedSnowmeltRate(),
                                WaterSimulationConfig.watershedGroundwaterEnabled(),
                                WaterSimulationConfig.watershedGroundwaterRechargeRate(),
                                WaterSimulationConfig.watershedGroundwaterSeepageRate(),
                                WaterSimulationConfig.watershedSpringThreshold()
                        )
                );
                boolean changed = state.apply(
                        result,
                        WaterSimulationConfig.watershedFloodThreshold(),
                        gameTime,
                        WaterSimulationConfig.watershedSedimentEffectsEnabled()
                                ? com.thunder.wildernessodysseyapi.watersystem.water.erosion.ErosionSavedData
                                .get(level).units(chunkKey) / 32.0f : 0.0f
                );
                if (downstream.state != null && result.downstreamTransfer() > 0.0f) {
                    changed |= downstream.state.addIncomingRunoff(result.downstreamTransfer());
                }
                if (changed) {
                    data.markChanged();
                }
                if (placementBudget > floodPlacements && WaterSimulationConfig.localizedFloodingEnabled()) {
                    int added = TemporaryFloodManager.expand(
                            level,
                            data,
                            chunkKey,
                            state,
                            placementBudget - floodPlacements
                    );
                    floodPlacements += added;
                    placements += added;
                }
                if (standingWaterBudget > standingWaterPlacements
                        && WaterSimulationConfig.rainFedSurfaceWaterEnabled()) {
                    int added = RainwaterBodyManager.expand(
                            level,
                            data,
                            chunkKey,
                            state,
                            standingWaterBudget - standingWaterPlacements
                    );
                    standingWaterPlacements += added;
                    placements += added;
                }
                processed++;
            }
        } else {
            runtime.clear();
        }

        // Recession remains active after the watershed toggle is disabled so a
        // configuration change cannot strand exact temporary water forever.
        int removals = TemporaryFloodManager.recede(
                level,
                data,
                WaterSimulationConfig.maximumFloodRemovalsPerTick()
        );
        int activeFlood = TemporaryFloodSavedData.get(level).size();
        long elapsed = System.nanoTime() - started;
        WatershedSimulationDiagnostics.publish(
                level,
                runtime.queue.size(),
                processed,
                initialized,
                placements,
                removals,
                activeFlood,
                elapsed
        );
        if (WaterSimulationConfig.watershedDebugLoggingEnabled()
                && Math.floorMod(level.getGameTime(), DEBUG_LOG_INTERVAL_TICKS) == 0L) {
            ModConstants.LOGGER.info(
                    "Watershed {}: queued={}, processed={}, states={}, floodCells={}, placed={}, removed={}, micros={}",
                    level.dimension().location(),
                    runtime.queue.size(),
                    processed,
                    data.size(),
                    activeFlood,
                    placements,
                    removals,
                    elapsed / 1_000L
            );
        }
    }

    /** Releases only the unloading dimension's ephemeral queue and diagnostics. */
    public static void clearLevel(ServerLevel level) {
        RUNTIMES.remove(level);
        WatershedSimulationDiagnostics.clear(level);
    }

    private static int enqueuePlayerRelevant(
            ServerLevel level,
            WatershedSavedData data,
            RuntimeState runtime
    ) {
        int radius = WaterSimulationConfig.watershedSimulationDistanceChunks();
        int initialized = 0;
        Set<Long> visited = new HashSet<>();
        for (var player : level.players()) {
            int centerX = player.getBlockX() >> 4;
            int centerZ = player.getBlockZ() >> 4;
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                    int chunkX = centerX + offsetX;
                    int chunkZ = centerZ + offsetZ;
                    long key = ChunkPos.asLong(chunkX, chunkZ);
                    if (!visited.add(key)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    if (data.state(key) == null) {
                        initialized++;
                    }
                    data.getOrCreate(level, chunk);
                    runtime.offer(key);
                }
            }
        }
        return initialized;
    }

    private static Downstream downstream(
            ServerLevel level,
            WatershedSavedData data,
            long sourceKey,
            WatershedConditions.DrainageDirection direction
    ) {
        if (direction == null || direction == WatershedConditions.DrainageDirection.SINK) {
            return Downstream.UNAVAILABLE;
        }
        int chunkX = ChunkPos.getX(sourceKey) + direction.stepX();
        int chunkZ = ChunkPos.getZ(sourceKey) + direction.stepZ();
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return Downstream.UNAVAILABLE;
        }
        return new Downstream(data.getOrCreate(level, chunk));
    }

    private static void reconcileLoadedConnections(
            ServerLevel level,
            WatershedSavedData data,
            long loadedChunkKey
    ) {
        WatershedBasinSavedData basins = WatershedBasinSavedData.get(level);
        WatershedChunkState loadedState = data.state(loadedChunkKey);
        if (loadedState == null) {
            return;
        }
        mergeDownstreamIfLoaded(level, data, basins, loadedChunkKey, loadedState);
        int loadedX = ChunkPos.getX(loadedChunkKey);
        int loadedZ = ChunkPos.getZ(loadedChunkKey);
        for (WatershedConditions.DrainageDirection direction : WatershedConditions.DrainageDirection.values()) {
            if (direction == WatershedConditions.DrainageDirection.SINK) {
                continue;
            }
            int neighborX = loadedX - direction.stepX();
            int neighborZ = loadedZ - direction.stepZ();
            LevelChunk neighbor = level.getChunkSource().getChunkNow(neighborX, neighborZ);
            if (neighbor == null) {
                continue;
            }
            WatershedChunkState neighborState = data.getOrCreate(level, neighbor);
            if (neighborState.conditions().downstreamDirection() == direction) {
                basins.union(neighborState.localBasinId(), loadedState.localBasinId());
            }
        }
    }

    private static void mergeDownstreamIfLoaded(
            ServerLevel level,
            WatershedSavedData data,
            WatershedBasinSavedData basins,
            long sourceKey,
            WatershedChunkState source
    ) {
        Downstream downstream = downstream(level, data, sourceKey, source.conditions().downstreamDirection());
        if (downstream.state != null) {
            basins.union(source.localBasinId(), downstream.state.localBasinId());
        }
    }

    private static BlockPos samplePosition(
            ServerLevel level,
            LevelChunk chunk,
            WatershedChunkState state
    ) {
        if (state.representativePosition() != WatershedChunkState.NO_REPRESENTATIVE) {
            return BlockPos.of(state.representativePosition()).above();
        }
        return new BlockPos(
                chunk.getPos().getMiddleBlockX(),
                level.getSeaLevel(),
                chunk.getPos().getMiddleBlockZ()
        );
    }

    private static final class RuntimeState {
        private final ArrayDeque<Long> queue = new ArrayDeque<>();
        private final Set<Long> queued = new HashSet<>();

        private void offer(long chunkKey) {
            if (queued.add(chunkKey)) {
                queue.addLast(chunkKey);
            }
        }

        private Long poll() {
            Long key = queue.pollFirst();
            if (key != null) {
                queued.remove(key);
            }
            return key;
        }

        private void clear() {
            queue.clear();
            queued.clear();
        }
    }

    private record Downstream(WatershedChunkState state) {
        private static final Downstream UNAVAILABLE = new Downstream(null);
    }
}
