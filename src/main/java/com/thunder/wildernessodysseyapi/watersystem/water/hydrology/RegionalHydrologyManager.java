package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;
import java.util.WeakHashMap;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.*;

/** Server-thread owner for persistent, player-independent coarse hydrology and loaded projections. */
public final class RegionalHydrologyManager {
    private static final Map<ServerLevel, Runtime> RUNTIMES = new WeakHashMap<>();

    private RegionalHydrologyManager() { }

    /** Admission only uses a chunk already supplied by the normal world lifecycle. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk, WatershedChunkState metadata) {
        RegionalHydrologySavedData data = RegionalHydrologySavedData.get(level);
        long key = chunk.getPos().toLong();
        RegionalHydrologyState state = data.state(key);
        if (state == null) {
            state = new RegionalHydrologyState(key, level.getGameTime(), metadata.conditions().averageTerrainElevation());
            state.feature = metadata.baseWaterFeature();
            state.ocean = state.feature == WatershedConditions.WaterFeature.COASTAL;
            RegionalSoilSampler.sample(chunk, state);
            // Legacy normalized water is not a recoverable quantity. Begin dry, preserving
            // all legacy canonical/SPH/generated water separately without copying it.
            if (!data.admit(state, RegionalHydrologyConfig.maximumRegions())) return;
            refreshForcing(level, state);
        }
        AtmosphericWaterExchange.publish(level, chunk.getPos(), 0, 0, state.stored(SNOW),
                RegionalHydrologyState.AREA, state.lastSimulationTick);
    }

    /** Invalidates cached DEM only for an edited loaded terrain cell; retains exact inventory. */
    public static void terrainChanged(ServerLevel level, LevelChunk chunk, WatershedChunkState metadata) {
        RegionalHydrologySavedData data = RegionalHydrologySavedData.get(level);
        RegionalHydrologyState state = data.state(chunk.getPos().toLong());
        if (state == null) return;
        state.elevation = metadata.conditions().averageTerrainElevation();
        RegionalSoilSampler.sample(chunk, state);
        data.terrainChanged();
    }

    public static void tickLevel(ServerLevel level, WatershedSavedData metadata) {
        long started = System.nanoTime();
        if (!WaterSimulationConfig.watershedSimulationEnabled() && !WaterSimulationConfig.weatherHydrologyEnabled()) {
            clearLevel(level);
            int removed = TemporaryFloodManager.recede(level, metadata, WaterSimulationConfig.maximumFloodRemovalsPerTick());
            WatershedSimulationDiagnostics.publish(level, 0, 0, 0, 0, removed,
                    TemporaryFloodSavedData.get(level).size(), System.nanoTime() - started);
            return;
        }
        RegionalHydrologySavedData data = RegionalHydrologySavedData.get(level);
        Runtime runtime = RUNTIMES.computeIfAbsent(level, ignored -> new Runtime());
        if (runtime.lastTick == level.getGameTime()) return;
        runtime.lastTick = level.getGameTime();
        if (runtime.graph == null && runtime.topologyRevision != data.terrainRevision()) {
            runtime.graph = new RegionalDrainageGraph(data.regions());
            runtime.buildRevision = data.terrainRevision();
        }
        if (runtime.graph != null && runtime.graph.advance(RegionalHydrologyConfig.topologyWork())) {
            runtime.graph = null;
            runtime.topologyRevision = runtime.buildRevision;
            data.setDirty();
        }
        int processed = 0;
        int placements = 0;
        int floodPlacements = 0;
        int standingPlacements = 0;
        RegionalHydrologyModel.Parameters parameters = new RegionalHydrologyModel.Parameters(
                RegionalHydrologyConfig.rainfall(), RegionalHydrologyConfig.evaporation(),
                RegionalHydrologyConfig.roughness(), RegionalHydrologyConfig.bankfullDepth(),
                RegionalHydrologyConfig.aquiferTime(), WaterSimulationConfig.watershedGroundwaterEnabled(),
                RegionalHydrologyConfig.thermal());
        int count = Math.min(data.size(), WaterSimulationConfig.WATERSHED_CHUNKS_PER_TICK.get());
        for (int i = 0; i < count; i++) {
            RegionalHydrologyState state = data.next();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(state.key), ChunkPos.getZ(state.key));
            long now = level.getGameTime();
            if (now < state.lastSimulationTick) continue;
            int steps = 0;
            while (now - state.lastSimulationTick >= RegionalHydrologyModel.STEP_TICKS
                    && steps < RegionalHydrologyConfig.catchupSteps()) {
                // Cached forcing is the explicit zero-order historical approximation.
                // Refresh only after old intervals are caught up, never apply today's storm to an old gap.
                if (now - state.lastSimulationTick < RegionalHydrologyModel.STEP_TICKS * 2) {
                    refreshForcing(level, state);
                }
                RegionalHydrologyState target = runtime.graph == null ? data.state(state.downstream) : null;
                long stepTicks = catchupStepTicks(now - state.lastSimulationTick,
                        RegionalHydrologyConfig.catchupSteps() - steps++);
                AtmosphericWaterExchange.applyPrecipitation(level, state);
                RegionalHydrologyModel.Exchange exchange = RegionalHydrologyModel.advance(state, target, parameters, stepTicks / 20.0);
                state.lastSimulationTick += stepTicks;
                AtmosphericWaterExchange.publish(level, new ChunkPos(state.key), exchange.precipitationMilliUnits(),
                        exchange.evaporationMilliUnits(), state.stored(SNOW), RegionalHydrologyState.AREA, state.lastSimulationTick);
                data.setDirty();
                processed++;
            }
            if (steps > 0 && now - state.lastSimulationTick < RegionalHydrologyModel.STEP_TICKS) {
                // Longer final intervals can skip the short-interval refresh
                // above. Capture today's forcing for the next interval only.
                refreshForcing(level, state);
            }
            if (chunk == null) continue;
            WatershedChunkState view = metadata.getOrCreate(level, chunk);
            view.apply(conditions(state), WaterSimulationConfig.watershedFloodThreshold(), state.lastSimulationTick,
                    WaterSimulationConfig.watershedSedimentEffectsEnabled()
                            ? com.thunder.wildernessodysseyapi.watersystem.water.erosion.ErosionSavedData.get(level).units(state.key) / 32.0f : 0);
            view.applyRegionalDrainage(state.downstream, state.key, state.contributingArea);
            metadata.markChanged();
            if (WaterSimulationConfig.localizedFloodingEnabled()) {
                int added = TemporaryFloodManager.expand(level, metadata, state.key, view,
                        WaterSimulationConfig.maximumFloodPlacementsPerTick() - floodPlacements);
                floodPlacements += added;
                placements += added;
            }
            if (WaterSimulationConfig.rainFedSurfaceWaterEnabled()) {
                int added = RainwaterBodyManager.expand(level, metadata, state.key, view,
                        WaterSimulationConfig.surfaceWaterMaximumPlacementsPerTick() - standingPlacements);
                standingPlacements += added;
                placements += added;
            }
        }
        int removed = TemporaryFloodManager.recede(level, metadata, WaterSimulationConfig.maximumFloodRemovalsPerTick());
        WatershedSimulationDiagnostics.publish(level, data.size(), processed, 0, placements, removed,
                TemporaryFloodSavedData.get(level).size(), System.nanoTime() - started);
    }

    private static void refreshForcing(ServerLevel level, RegionalHydrologyState state) {
        boolean enabled = WaterSimulationConfig.weatherWaterCouplingEnabled() && WeatherConfig.dimensionEnabled(level.dimension());
        ChunkPos chunk = new ChunkPos(state.key);
        BlockPos position = new BlockPos(chunk.getMiddleBlockX(),
                (int) state.elevation + 1, chunk.getMiddleBlockZ());
        // WeatherAuthority.sample reads only its cached atmosphere grid, including
        // unloaded terrain; it never initializes terrain or requests a chunk.
        WeatherSample weather = enabled ? WeatherServices.query().sample(level, position) : WeatherSample.CLEAR;
        state.precipitationFraction = enabled ? weather.precipitationIntensity() : 0;
        state.snowing = weather.precipitationType().isFrozen();
        state.physicalPrecipitation = enabled;
        state.airTemperature = weather.temperature();
        state.humidity = enabled ? weather.humidity() : 1.0;
        state.wind = Math.min(1, Math.hypot(weather.wind().x(), weather.wind().z()));
        state.sunlight = Math.max(0, Math.cos((level.getDayTime() % 24000 - 6000) * Math.PI / 12000))
                * (1.0 - weather.cloudWater() * 0.8);
        state.forcingTick = level.getGameTime();
    }

    private static WatershedSimulationModel.Result conditions(RegionalHydrologyState state) {
        double m3 = HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK;
        float soil = unit(state.stored(SOIL) / (m3 * RegionalHydrologyState.AREA * state.soil.porosity()));
        float runoff = unit(state.stored(SURFACE_RUNOFF) / (m3 * 16));
        float discharge = unit(state.discharge / 8.0);
        boolean flood = state.stored(FLOODPLAIN) >= 1000 || state.stage > RegionalHydrologyConfig.bankfullDepth();
        float sediment = WaterSimulationConfig.watershedSedimentEffectsEnabled()
                ? unit(state.velocity * state.velocity * 0.02 + runoff * 0.1) : 0;
        double dx = state.downstream == RegionalHydrologyState.NO_OUTLET ? 0
                : ChunkPos.getX(state.downstream) - ChunkPos.getX(state.key);
        double dz = state.downstream == RegionalHydrologyState.NO_OUTLET ? 0
                : ChunkPos.getZ(state.downstream) - ChunkPos.getZ(state.key);
        double length = Math.max(1, Math.hypot(dx, dz));
        return new WatershedSimulationModel.Result(soil, unit(state.precipitationFraction), runoff, discharge,
                (float) Math.min(WaterSimulationConfig.watershedMaximumWaterLevelOffset(),
                        Math.max(0, state.stage - RegionalHydrologyConfig.bankfullDepth())),
                flood ? 1 : unit(state.stage / RegionalHydrologyConfig.bankfullDepth()), flood,
                sediment, 1 - sediment, (float) (dx / length * state.velocity / 20),
                (float) (dz / length * state.velocity / 20),
                WaterSimulationConfig.watershedDebrisEffectsEnabled() ? sediment * 0.25f : 0,
                0, unit(state.lastMelt), unit(state.lastRecharge),
                unit(state.stored(GROUNDWATER) / (m3 * RegionalHydrologyState.AREA * 4)), unit(state.lastBaseflow));
    }

    private static float unit(double value) { return (float) Math.max(0, Math.min(1, value)); }

    /** Exponential stores permit coarse catch-up without replaying every two-second interval. */
    static long catchupStepTicks(long elapsed, int remainingSteps) {
        long intervals = elapsed / RegionalHydrologyModel.STEP_TICKS;
        long perStep = Math.max(1, (intervals + Math.max(1, remainingSteps) - 1) / Math.max(1, remainingSteps));
        return Math.min(72000, Math.min(intervals, perStep) * RegionalHydrologyModel.STEP_TICKS);
    }
    public static void clearLevel(ServerLevel level) {
        RUNTIMES.remove(level);
        AtmosphericWaterExchange.clearLevel(level);
    }
    private static final class Runtime {
        private long lastTick = Long.MIN_VALUE;
        private long topologyRevision = -1;
        private long buildRevision;
        private RegionalDrainageGraph graph;
    }
}
