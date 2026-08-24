package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherForecast;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.config.VanillaWeatherCompatibilityMode;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.VanillaWeatherCommandAdapter;
import com.thunder.wildernessodysseyapi.weather.integration.WildernessWeatherWaterInfluence;
import com.thunder.wildernessodysseyapi.weather.integration.season.SeasonalWeatherIntegrations;
import com.thunder.wildernessodysseyapi.weather.integration.survival.SurvivalWeatherIntegrations;
import com.thunder.wildernessodysseyapi.weather.lightning.LocalizedLightningScheduler;
import com.thunder.wildernessodysseyapi.weather.forecast.WeatherForecastService;
import com.thunder.wildernessodysseyapi.weather.forecast.RegionalWeatherThreatCache;
import com.thunder.wildernessodysseyapi.weather.forecast.WeatherThreatForecastService;
import com.thunder.wildernessodysseyapi.weather.networking.DistantThunderSnapshotManager;
import com.thunder.wildernessodysseyapi.weather.networking.WeatherRegionSyncPayload;
import com.thunder.wildernessodysseyapi.weather.networking.WeatherSnapshotManager;
import com.thunder.wildernessodysseyapi.weather.storage.AtmosphereSavedData;
import com.thunder.wildernessodysseyapi.weather.storage.WeatherSystemsSavedData;
import com.thunder.wildernessodysseyapi.weather.surface.SurfaceWeatheringScheduler;
import com.thunder.wildernessodysseyapi.weather.severe.SevereWeatherScheduler;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemInfluenceModel;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import com.thunder.wildernessodysseyapi.weather.wildfire.WildfireRiskModel;
import com.thunder.wildernessodysseyapi.weather.wildfire.WildfireScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;

/**
 * Singular server authority for dimension-local atmospheric weather.
 *
 * <p>The authority identifies player-relevant cells, captures immutable world
 * inputs on the server thread, calculates from frozen neighborhoods, and
 * applies results with generation and revision checks. The Data Engine may run
 * only the immutable calculation phase on a shared worker; capture, validation,
 * persistence, and every Minecraft mutation remain on the server thread.</p>
 */
public final class WeatherAuthority implements WeatherQuery {

    private static final int MAX_CATCH_UP_STEPS = 12;
    private static final double PERSISTENT_STORM_ENERGY = 0.55;
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private static final WeatherAuthority INSTANCE = new WeatherAuthority();

    private final AtmosphereSimulationEngine engine = new AtmosphereSimulationEngine();
    private final Map<ServerLevel, LevelRuntime> runtimes = new ConcurrentHashMap<>();

    private WeatherAuthority() {
    }

    /** Returns the process-wide authority that resolves state per supplied level. */
    public static WeatherAuthority get() {
        return INSTANCE;
    }

    /** Advances one complete dimension pass without an external time budget. */
    public void tick(ServerLevel level) {
        OptionalMaintenanceResult maintenance = runOptionalMaintenance(level);
        tickWorldEffects(level, ALWAYS_HAS_TIME);
        if (maintenance.snapshotDue()) {
            publishSnapshot(level);
        }
    }

    /**
     * Advances optional atmospheric simulation and requests due publication.
     *
     * <p>This method is server-thread-only. The Data Engine invokes it as one
     * bounded per-level callback; it does not send packets or apply unrelated
     * world effects. Missed invocations produce one elapsed-time pass instead
     * of replaying every missed scheduler boundary.</p>
     */
    public OptionalMaintenanceResult runOptionalMaintenance(ServerLevel level) {
        OptionalMaintenancePreparation preparation = prepareOptionalMaintenance(level, true);
        SimulationBatch batch = preparation.simulationBatch();
        if (batch != null) {
            markSimulationBatchScheduled(level, batch);
            SimulationResult result = calculateSimulationBatch(batch);
            applySimulationResult(level, result);
        }
        return new OptionalMaintenanceResult(preparation.simulationDue(), preparation.snapshotDue());
    }

    /**
     * Captures due optional weather work without performing atmospheric math.
     *
     * <p>SERVER THREAD ONLY. When {@code allowSimulationCapture} is false, a
     * due autonomous pass remains due so the caller can retry after its current
     * worker task completes. Vanilla command maintenance is always applied here
     * because it directly owns authoritative weather for its configured span.</p>
     */
    public OptionalMaintenancePreparation prepareOptionalMaintenance(
            ServerLevel level,
            boolean allowSimulationCapture
    ) {
        Objects.requireNonNull(level, "level");
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        long gameTime = level.getGameTime();
        LevelRuntime runtime = runtime(level);
        boolean simulationDue = isElapsedDue(
                gameTime,
                runtime.lastSimulationMaintenanceTick,
                scheduling.simulationIntervalTicks()
        );
        boolean snapshotDue = isElapsedDue(
                gameTime,
                runtime.lastSnapshotRequestTick,
                scheduling.snapshotSyncIntervalTicks()
        );
        boolean enabled = WeatherConfig.dimensionEnabled(level.dimension());
        if (!enabled) {
            if (snapshotDue) {
                runtime.lastSnapshotRequestTick = gameTime;
            }
            return new OptionalMaintenancePreparation(false, snapshotDue, null);
        }

        AtmosphereSavedData data = data(level, scheduling, runtime);
        expireVanillaCommandWeather(level, runtime, data, gameTime);
        SimulationBatch batch = null;
        if (runtime.vanillaWeatherState != null) {
            // The vanilla command owns weather for its requested duration.
            // Refresh on simulation and sync boundaries so moving players do
            // not briefly receive autonomous weather in newly relevant cells.
            if (simulationDue || snapshotDue) {
                maintainVanillaCommandWeather(level, runtime, data, scheduling, gameTime);
            }
        } else if (simulationDue && allowSimulationCapture) {
            batch = captureSimulationBatch(
                    level,
                    runtime,
                    data,
                    scheduling,
                    WeatherConfig.settings(),
                    gameTime
            );
        }

        if (simulationDue && runtime.vanillaWeatherState != null) {
            runtime.lastSimulationMaintenanceTick = gameTime;
        }
        if (snapshotDue) {
            runtime.lastSnapshotRequestTick = gameTime;
        }
        return new OptionalMaintenancePreparation(simulationDue, snapshotDue, batch);
    }

    /**
     * Runs loaded-world weather effects while repeatedly honoring Minecraft's
     * live spare-time allowance.
     *
     * <p>Every callback remains on the logical server thread. Atmospheric
     * calculation scheduling and client publication are handled separately so
     * a slow effect cannot accidentally authorize later optional work.</p>
     */
    public void tickWorldEffects(ServerLevel level, BooleanSupplier serverHasTime) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            return;
        }

        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        long gameTime = level.getGameTime();
        LevelRuntime runtime = runtime(level);
        AtmosphereSavedData data = data(level, scheduling, runtime);
        if (data.grid().ensureCellSize(scheduling.cellSize())) {
            data.markChanged();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }

        if (scheduling.compatibilityMode() == VanillaWeatherCompatibilityMode.SUPPRESS_GLOBAL
                && (level.isRaining() || level.isThundering())) {
            // This opt-in mode removes the global fallback. Position-aware
            // adapters and Wilderness gameplay consumers already read locally.
            level.setWeatherParameters(6_000, 0, false, false);
        }
        expireVanillaCommandWeather(level, runtime, data, gameTime);

        if (!serverHasTime.getAsBoolean()) {
            return;
        }
        runtime.lightningScheduler.tick(
                level,
                gameTime,
                scheduling.cellSize(),
                WeatherConfig.lightning()
        );
        if (!serverHasTime.getAsBoolean()) {
            return;
        }
        runtime.wildfireScheduler.tick(
                level,
                gameTime,
                scheduling.cellSize(),
                scheduling.environmentResampleIntervalTicks(),
                WeatherConfig.wildfires()
        );
        if (!serverHasTime.getAsBoolean()) {
            return;
        }
        WeatherConfig.FeatureSettings features = WeatherConfig.features();
        runtime.surfaceWeatheringScheduler.tick(level, gameTime, this, features);
        if (!serverHasTime.getAsBoolean()) {
            return;
        }
        List<TrackedWeatherSystem> trackedSystems = systems(level);
        runtime.severeWeatherScheduler.tick(level, gameTime, trackedSystems, features);
        if (!serverHasTime.getAsBoolean()) {
            return;
        }
        SurvivalWeatherIntegrations.tick(level, this);
    }

    /** Publishes one due authoritative regional snapshot on the server thread. */
    public void publishSnapshot(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            WeatherSnapshotManager.syncDisabled(
                    level,
                    scheduling.cellSize(),
                    synchronizationRadius(scheduling)
            );
            DistantThunderSnapshotManager.syncDisabled(level);
            return;
        }

        AtmosphereGrid grid = data(level, scheduling, runtime(level)).grid();
        WeatherSnapshotManager.syncLevel(
                level,
                grid,
                true,
                scheduling.cellSize(),
                synchronizationRadius(scheduling)
        );
        DistantThunderSnapshotManager.syncLevel(level, grid, systems(level));
    }

    @Override
    public WeatherSample sample(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return WeatherSample.CLEAR;
        }
        return queryGrid(level).sample(position);
    }

    /** Samples the existing optional season adapter without exposing its implementation. */
    @Override
    public SeasonalClimateState seasonalClimateAt(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return SeasonalClimateState.NONE;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        AtmosphereCellKey key = AtmosphereCellKey.fromBlock(
                position.getX(),
                position.getZ(),
                scheduling.cellSize()
        );
        AtmosphereEnvironment environment = runtime(level).inputSampler.sample(
                level,
                key,
                scheduling.cellSize(),
                scheduling.environmentResampleIntervalTicks()
        );
        return new SeasonalClimateState(
                environment.seasonalTemperatureOffset(),
                environment.seasonalEvaporationMultiplier(),
                environment.fireSeasonFactor(),
                environment.snowSeasonFactor(),
                environment.seasonCalendarAvailable()
        );
    }

    /**
     * Returns a region-cached projection of the existing moving weather systems.
     *
     * <p>The forecast uses the same tracker speed, lifecycle, and dissipation
     * controls as simulation. It never searches atmosphere cells or creates a
     * second weather state.</p>
     */
    @Override
    public WeatherThreatForecast getApproachingWeather(
            ServerLevel level,
            BlockPos position,
            int lookAheadTicks
    ) {
        if (level == null || position == null || lookAheadTicks <= 0
                || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return WeatherThreatForecast.NONE;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        WeatherConfig.FeatureSettings features = WeatherConfig.features();
        WeatherSystemTracker.TrackingSettings tracking =
                features.trackingSettings(scheduling.simulationIntervalTicks());
        LevelRuntime runtime = runtime(level);
        return runtime.approachingWeatherCache.query(
                position,
                scheduling.cellSize(),
                lookAheadTicks,
                level.getGameTime(),
                regionCenter -> WeatherThreatForecastService.forecast(
                        regionCenter.getX() + 0.5,
                        regionCenter.getZ() + 0.5,
                        lookAheadTicks,
                        systems(level),
                        tracking
                )
        );
    }

    /** Uses the cached primitive grid path for vanilla's frequent rain checks. */
    @Override
    public boolean isRainingAt(ServerLevel level, BlockPos position) {
        PrecipitationType type = precipitationTypeAt(level, position);
        return type == PrecipitationType.RAIN || type == PrecipitationType.HAIL;
    }

    /** Uses the cached primitive grid path for localized snow checks. */
    @Override
    public boolean isSnowingAt(ServerLevel level, BlockPos position) {
        return precipitationTypeAt(level, position) == PrecipitationType.SNOW;
    }

    /** Returns allocation-free primitive precipitation from the attached level grid. */
    @Override
    public double precipitationIntensityAt(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return 0.0;
        }
        return queryGrid(level).precipitationIntensity(position.getX(), position.getZ());
    }

    /** Returns allocation-free functional rain/snow classification from the attached grid. */
    @Override
    public PrecipitationType precipitationTypeAt(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return PrecipitationType.NONE;
        }
        return queryGrid(level).functionalPrecipitationType(position.getX(), position.getZ());
    }

    /** Returns the immutable cell containing a position, or {@code null}. */
    public AtmosphereView cellAt(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return null;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        AtmosphereGrid grid = data(level, scheduling).grid();
        return grid.view(AtmosphereCellKey.fromBlock(
                position.getX(),
                position.getZ(),
                scheduling.cellSize()
        ));
    }

    /** Returns all retained immutable cells for diagnostics and persistence tooling. */
    public List<AtmosphereView> cells(ServerLevel level) {
        if (level == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return List.of();
        }
        return data(level, WeatherConfig.scheduling()).grid().views();
    }

    /** Returns persistent storm and front identities for diagnostics and forecasting. */
    public List<TrackedWeatherSystem> systems(ServerLevel level) {
        if (level == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return List.of();
        }
        return WeatherSystemsSavedData.get(level, WeatherConfig.features()).tracker().systems();
    }

    /** Returns the campfire wildfire risk currently sampled at one position. */
    public WildfireRiskModel.RiskProfile wildfireRisk(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return WildfireRiskModel.evaluate(WeatherSample.CLEAR, AtmosphereEnvironment.TEMPERATE);
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        return runtime(level).wildfireScheduler.riskAt(
                level,
                position,
                scheduling.cellSize(),
                scheduling.environmentResampleIntervalTicks()
        );
    }

    /** Forces one nearby safe-bounded campfire ignition for operator verification. */
    public WildfireScheduler.IgnitionResult forceWildfireIgnition(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return WildfireScheduler.IgnitionResult.DISABLED;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        return runtime(level).wildfireScheduler.forceIgnition(
                level,
                position,
                scheduling.cellSize(),
                WeatherConfig.wildfires()
        );
    }

    /** Forecasts local pressure tendency and the nearest approaching front or storm. */
    public WeatherForecast forecast(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return WeatherForecastService.forecast(WeatherSample.CLEAR, 0.0, 0.0, 0.0, List.of(), 0.0);
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        WeatherConfig.FeatureSettings features = WeatherConfig.features();
        AtmosphereGrid grid = queryGrid(level);
        WeatherSample current = grid.sample(position);
        int upstreamX = (int) Math.round(position.getX() - current.wind().x() * scheduling.cellSize());
        int upstreamZ = (int) Math.round(position.getZ() - current.wind().z() * scheduling.cellSize());
        WeatherSample upstream = grid.sample(new BlockPos(upstreamX, position.getY(), upstreamZ));
        double trend = upstream.pressure() - current.pressure();
        return WeatherForecastService.forecast(
                current,
                trend,
                position.getX() + 0.5,
                position.getZ() + 0.5,
                systems(level),
                features.movementBlocksPerSecond()
        );
    }

    /** Applies one operator-controlled scalar to the containing cell. */
    public boolean setField(ServerLevel level, BlockPos position, ControlField field, double value) {
        Objects.requireNonNull(field, "field");
        CellContext context = ensureCell(level, position);
        if (context == null) {
            return false;
        }
        WeatherSample old = context.view.sample();
        WeatherSample next = switch (field) {
            case TEMPERATURE -> copy(old, value, old.humidity(), old.pressure(), old.cloudWater(),
                    old.instability(), old.stormEnergy(), old.precipitationIntensity(), old.precipitationType());
            case HUMIDITY -> copy(old, old.temperature(), value, old.pressure(), old.cloudWater(),
                    old.instability(), old.stormEnergy(), old.precipitationIntensity(), old.precipitationType());
            case PRESSURE -> copy(old, old.temperature(), old.humidity(), value, old.cloudWater(),
                    old.instability(), old.stormEnergy(), old.precipitationIntensity(), old.precipitationType());
            case STORM_ENERGY -> copy(old, old.temperature(), old.humidity(), old.pressure(), old.cloudWater(),
                    old.instability(), value, old.precipitationIntensity(), old.precipitationType());
        };
        return forceContext(context, next, level.getGameTime());
    }

    /** Forces localized rain, snow, or hail in the 3 by 3 cells around an operator. */
    public int forcePrecipitation(ServerLevel level, BlockPos position, PrecipitationType type) {
        if (type == null || type == PrecipitationType.NONE || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return 0;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        AtmosphereSavedData data = data(level, scheduling);
        AtmosphereCellKey center = AtmosphereCellKey.fromBlock(position.getX(), position.getZ(), scheduling.cellSize());
        int changed = 0;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                AtmosphereCellKey key = new AtmosphereCellKey(center.x() + offsetX, center.z() + offsetZ);
                AtmosphereView view = ensureCell(level, data, key, scheduling);
                WeatherSample old = view.sample();
                double temperature = type == PrecipitationType.SNOW
                        ? Math.min(0.0, old.temperature()) : Math.max(5.0, old.temperature());
                WeatherSample forced = new WeatherSample(
                        temperature,
                        Math.max(0.92, old.humidity()),
                        Math.min(0.96, old.pressure()),
                        old.wind(),
                        Math.max(0.88, old.cloudWater()),
                        Math.max(0.58, old.instability()),
                        Math.max(0.48, old.stormEnergy()),
                        0.90,
                        type,
                        old.verticalMotion(),
                        old.cloudDepth(),
                        old.cloudWind(),
                        old.surface()
                );
                if (data.grid().force(key, forced, level.getGameTime())) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            data.markChanged();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
        return changed;
    }

    /**
     * Forces one derived cloud genus in the 3 by 3 cells around an operator.
     *
     * <p>The synchronized atmospheric fields remain authoritative. Clients
     * derive the requested type through the normal classifier and render it
     * through the normal continuous cloud atlas.</p>
     */
    public int forceCloudType(ServerLevel level, BlockPos position, CloudType type) {
        if (type == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return 0;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        AtmosphereSavedData data = data(level, scheduling);
        AtmosphereCellKey center = AtmosphereCellKey.fromBlock(
                position.getX(),
                position.getZ(),
                scheduling.cellSize()
        );
        int changed = 0;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                AtmosphereCellKey key = new AtmosphereCellKey(center.x() + offsetX, center.z() + offsetZ);
                AtmosphereView view = ensureCell(level, data, key, scheduling);
                WeatherSample forced = CloudDebugPreset.apply(type, view.sample());
                if (data.grid().force(key, forced, level.getGameTime())) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            data.markChanged();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
        return changed;
    }

    /** Clears cloud moisture, precipitation, and storm energy from the local 3 by 3 cell area. */
    public int clearLocalWeather(ServerLevel level, BlockPos position) {
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            return 0;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        AtmosphereSavedData data = data(level, scheduling);
        AtmosphereCellKey center = AtmosphereCellKey.fromBlock(position.getX(), position.getZ(), scheduling.cellSize());
        int changed = 0;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                AtmosphereCellKey key = new AtmosphereCellKey(center.x() + offsetX, center.z() + offsetZ);
                AtmosphereView view = ensureCell(level, data, key, scheduling);
                WeatherSample old = view.sample();
                WeatherSample clear = new WeatherSample(
                        old.temperature(),
                        Math.min(old.humidity(), 0.55),
                        Math.max(old.pressure(), 1.0),
                        old.wind(),
                        0.0,
                        Math.min(old.instability(), 0.25),
                        0.0,
                        0.0,
                        PrecipitationType.NONE,
                        old.verticalMotion(),
                        old.cloudDepth(),
                        old.cloudWind(),
                        old.surface()
                );
                if (data.grid().force(key, clear, level.getGameTime())) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            data.markChanged();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
        return changed;
    }

    /**
     * Mirrors a vanilla weather command across this dimension's retained and
     * player-relevant atmospheric cells for the exact vanilla duration.
     *
     * <p>The command mixin calls this only after vanilla has committed its own
     * global weather parameters. Normal atmospheric simulation pauses while the
     * override is active, then resumes from clear command-created state.</p>
     *
     * @return the number of atmospheric cells changed immediately
     */
    public int applyVanillaCommandWeather(
            ServerLevel level,
            VanillaWeatherCommandAdapter.State state,
            int durationTicks
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            return 0;
        }

        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        LevelRuntime runtime = runtime(level);
        AtmosphereSavedData data = data(level, scheduling, runtime);
        long gameTime = level.getGameTime();
        runtime.beginVanillaWeather(state, gameTime, durationTicks);

        // Vanilla weather commands are dimension-wide. Existing dormant cells
        // and every currently player-relevant cell receive the same override.
        Set<Long> targetKeys = collectActiveKeys(level, scheduling);
        for (AtmosphereView view : data.grid().views()) {
            targetKeys.add(view.key().packed());
        }
        int changed = applyVanillaState(
                level,
                runtime,
                data,
                scheduling,
                targetKeys,
                state,
                gameTime,
                true
        );
        if (changed > 0) {
            data.markChanged();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
        return changed;
    }

    /** Describes why a retained cell is currently scheduled or dormant. */
    public Activity activity(ServerLevel level, AtmosphereView view) {
        if (level == null || view == null) {
            return Activity.DORMANT;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        if (view.sample().stormEnergy() >= PERSISTENT_STORM_ENERGY) {
            return Activity.PERSISTENT_STORM;
        }
        long age = Math.max(0L, level.getGameTime() - view.lastActiveTick());
        return age <= scheduling.simulationIntervalTicks()
                ? Activity.ACTIVE
                : age <= scheduling.inactiveCellGracePeriodTicks() ? Activity.GRACE : Activity.DORMANT;
    }

    /** Invalidates cached inputs and forces full snapshots after config reload. */
    public synchronized void onConfigurationReload() {
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        for (Map.Entry<ServerLevel, LevelRuntime> entry : runtimes.entrySet()) {
            data(entry.getKey(), scheduling, entry.getValue());
            entry.getValue().invalidateSimulationGeneration();
            entry.getValue().inputSampler.clear();
            entry.getValue().lightningScheduler.reset();
            entry.getValue().wildfireScheduler.reset();
            entry.getValue().approachingWeatherCache.clear();
            entry.getValue().lastSimulationMaintenanceTick = Long.MIN_VALUE;
            entry.getValue().lastSnapshotRequestTick = Long.MIN_VALUE;
            WeatherSnapshotManager.markLevelDirty(entry.getKey().dimension());
        }
    }

    /** Releases only the unloading dimension's ephemeral caches. */
    public synchronized void unload(ServerLevel level) {
        LevelRuntime runtime = runtimes.remove(level);
        if (runtime != null) {
            runtime.inputSampler.clear();
            runtime.lightningScheduler.reset();
            runtime.wildfireScheduler.reset();
            runtime.approachingWeatherCache.clear();
        }
        WeatherSnapshotManager.clearLevel(level.dimension());
        DistantThunderSnapshotManager.clearLevel(level.dimension());
    }

    /** Releases server-process synchronization and sampling state at shutdown. */
    public synchronized void shutdown() {
        for (LevelRuntime runtime : runtimes.values()) {
            runtime.inputSampler.clear();
            runtime.lightningScheduler.reset();
            runtime.wildfireScheduler.reset();
            runtime.approachingWeatherCache.clear();
        }
        runtimes.clear();
        WeatherSnapshotManager.clear();
        DistantThunderSnapshotManager.clear();
    }

    /** Marks a lifecycle-changing player for a complete next snapshot. */
    public void markPlayerDirty(ServerPlayer player) {
        WeatherSnapshotManager.markPlayerDirty(player);
        DistantThunderSnapshotManager.markPlayerDirty(player);
    }

    /** Forgets synchronization state when a player disconnects. */
    public void forgetPlayer(ServerPlayer player) {
        WeatherSnapshotManager.forgetPlayer(player);
        DistantThunderSnapshotManager.forgetPlayer(player);
    }

    private SimulationBatch captureSimulationBatch(
            ServerLevel level,
            LevelRuntime runtime,
            AtmosphereSavedData data,
            WeatherConfig.SchedulingSettings scheduling,
            SimulationSettings settings,
            long gameTime
    ) {
        AtmosphereGrid grid = data.grid();
        WeatherConfig.FeatureSettings features = WeatherConfig.features();
        WeatherSystemsSavedData systemsData = WeatherSystemsSavedData.get(level, features);
        WeatherSystemTracker tracker = systemsData.tracker();
        Set<Long> activeKeys = collectActiveKeys(level, scheduling);

        // New cells sample loaded environmental context once, then become
        // normal compact state shared by overlapping players.
        for (long packedKey : activeKeys) {
            AtmosphereCellKey key = AtmosphereCellKey.fromPacked(packedKey);
            ensureCell(level, data, key, scheduling);
            grid.markActive(key, gameTime);
        }
        if (!activeKeys.isEmpty()) {
            // Activity watermarks are persistent authority state even if a
            // later worker result is rejected as stale.
            data.markChanged();
        }

        Map<Long, AtmosphereView> previous = grid.snapshotByPackedKey();
        Set<Long> scheduledKeys = new HashSet<>(activeKeys);
        for (AtmosphereView view : previous.values()) {
            if (AtmosphereActivityPolicy.shouldSimulate(
                    view,
                    gameTime,
                    scheduling.inactiveCellGracePeriodTicks(),
                    PERSISTENT_STORM_ENERGY
            )) {
                scheduledKeys.add(view.key().packed());
            }
            if (view.sample().stormEnergy() >= PERSISTENT_STORM_ENERGY) {
                // Retain the existing cardinal ring around a detached storm so
                // pressure and moisture can continue crossing its boundary.
                addExistingCardinalNeighbors(scheduledKeys, previous, view.key());
            }
        }

        List<CellCalculationInput> inputs = new ArrayList<>(scheduledKeys.size());
        Map<Long, Long> expectedRevisions = new HashMap<>(Math.max(16, scheduledKeys.size() * 2));
        for (long packedKey : scheduledKeys) {
            AtmosphereView view = previous.get(packedKey);
            if (view == null) {
                continue;
            }
            AtmosphereEnvironment environment = runtime.inputSampler.sample(
                    level,
                    view.key(),
                    scheduling.cellSize(),
                    scheduling.environmentResampleIntervalTicks()
            );
            AtmosphereSimulationEngine.Neighborhood neighbors = neighborhood(previous, view);
            int catchUpSteps = AtmosphereActivityPolicy.catchUpSteps(
                    view,
                    gameTime,
                    scheduling.simulationIntervalTicks(),
                    MAX_CATCH_UP_STEPS
            );
            double centerX = (view.key().x() + 0.5) * scheduling.cellSize();
            double centerZ = (view.key().z() + 0.5) * scheduling.cellSize();
            inputs.add(new CellCalculationInput(
                    view.key(),
                    view.revision(),
                    view.sample(),
                    environment,
                    neighbors,
                    catchUpSteps,
                    tracker.influenceAt(centerX, centerZ),
                    centerX,
                    centerZ
            ));
            expectedRevisions.put(packedKey, view.revision());
        }
        return new SimulationBatch(
                runtime.simulationGeneration,
                gameTime,
                scheduling.cellSize(),
                scheduling.simulationIntervalTicks(),
                scheduling.maxPersistedCells(),
                scheduling.debugLogging(),
                settings,
                features,
                features.trackingSettings(scheduling.simulationIntervalTicks()),
                Set.copyOf(activeKeys),
                Map.copyOf(expectedRevisions),
                List.copyOf(inputs)
        );
    }

    /**
     * Performs pure atmospheric math from a detached server-thread capture.
     *
     * <p>WORKER SAFE. This method does not access levels, chunks, registries,
     * persistence, the runtime map, or any other mutable Minecraft owner.</p>
     */
    public SimulationResult calculateSimulationBatch(SimulationBatch batch) {
        Objects.requireNonNull(batch, "Simulation batch is required");
        List<CalculatedCell> calculated = new ArrayList<>(batch.inputs.size());
        List<WeatherSystemTracker.Observation> observations = new ArrayList<>();
        for (CellCalculationInput input : batch.inputs) {
            WeatherSample next = input.sample;
            for (int step = 0; step < input.catchUpSteps; step++) {
                next = engine.simulate(next, input.environment, input.neighborhood, batch.settings);
            }
            next = WeatherSystemInfluenceModel.apply(
                    next,
                    input.systemInfluence,
                    batch.settings.maximumPrecipitationIntensity()
            );
            AtmosphericFrontModel.FrontState front = AtmosphericFrontModel.analyze(next, input.neighborhood);
            WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(
                    next, input.environment, front.type(), front.strength()
            );
            collectObservations(
                    observations,
                    next,
                    front,
                    hazards,
                    batch.features,
                    input.centerX,
                    input.centerZ,
                    batch.cellSize
            );
            calculated.add(new CalculatedCell(input.key, input.baseRevision, next));
        }
        return new SimulationResult(batch, List.copyOf(calculated), List.copyOf(observations));
    }

    /** SERVER THREAD ONLY. Marks an accepted batch as owning this cadence slot. */
    public boolean markSimulationBatchScheduled(ServerLevel level, SimulationBatch batch) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(batch, "Simulation batch is required");
        LevelRuntime runtime = runtimes.get(level);
        if (runtime == null || runtime.simulationGeneration != batch.generation) {
            return false;
        }
        runtime.lastSimulationMaintenanceTick = batch.gameTime;
        return true;
    }

    /**
     * SERVER THREAD ONLY. Rejects work captured before a lifecycle/config change
     * or any intervening authoritative edit to a participating cell.
     */
    public boolean isSimulationResultCurrent(ServerLevel level, SimulationResult result) {
        if (level == null || result == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return false;
        }
        LevelRuntime runtime = runtimes.get(level);
        SimulationBatch batch = result.batch;
        AtmosphereGrid grid = runtime == null ? null : runtime.atmosphereGrid;
        return runtime != null
                && runtime.simulationGeneration == batch.generation
                && grid != null
                && grid.cellSize() == batch.cellSize
                && result.calculated.size() == batch.inputs.size()
                && revisionsMatch(batch.expectedRevisions, packedKey -> {
                    AtmosphereView view = grid.view(packedKey);
                    return view == null ? null : view.revision();
                });
    }

    /**
     * SERVER THREAD ONLY. Applies one fully validated batch and updates its
     * persistent weather-system identities as a single logical pass.
     */
    public boolean applySimulationResult(ServerLevel level, SimulationResult result) {
        if (!isSimulationResultCurrent(level, result)) {
            return false;
        }
        SimulationBatch batch = result.batch;
        LevelRuntime runtime = runtimes.get(level);
        AtmosphereSavedData data = data(level, WeatherConfig.scheduling(), runtime);
        if (!isSimulationResultCurrent(level, result)) {
            return false;
        }
        AtmosphereGrid grid = data.grid();
        boolean changed = false;
        for (CalculatedCell calculated : result.calculated) {
            changed |= grid.applyIfRevision(
                    calculated.key,
                    calculated.baseRevision,
                    calculated.sample,
                    batch.gameTime
            );
        }
        Set<Long> protectedKeys = new HashSet<>(batch.activeKeys);
        WeatherConfig.SchedulingSettings currentScheduling = WeatherConfig.scheduling();
        if (currentScheduling.cellSize() == batch.cellSize) {
            protectedKeys.addAll(collectActiveKeys(level, currentScheduling));
        }
        int removed = grid.trimToLimit(batch.maximumPersistedCells, protectedKeys);
        WeatherSystemsSavedData systemsData = WeatherSystemsSavedData.get(level, batch.features);
        WeatherSystemTracker tracker = systemsData.tracker();
        boolean systemsChanged = tracker.update(
                result.observations,
                batch.gameTime,
                batch.simulationIntervalTicks,
                batch.trackingSettings
        );
        if (systemsChanged) {
            systemsData.markChanged();
            runtime.approachingWeatherCache.clear();
        }
        if (changed || removed > 0 || !batch.activeKeys.isEmpty() || !result.calculated.isEmpty()) {
            data.markChanged();
        }

        if (batch.debugLogging && isDue(batch.gameTime, 1_200)) {
            ModConstants.LOGGER.debug(
                    "Atmosphere {}: {} retained cells, {} active, {} simulated, {} evicted",
                    level.dimension().location(),
                    grid.size(),
                    batch.activeKeys.size(),
                    result.calculated.size(),
                    removed
            );
        }
        return true;
    }

    /** SERVER THREAD ONLY. Makes a discarded accepted batch due again. */
    public void requestSimulationRetry(ServerLevel level, SimulationBatch batch) {
        if (level == null || batch == null) {
            return;
        }
        LevelRuntime runtime = runtimes.get(level);
        if (runtime != null
                && runtime.simulationGeneration == batch.generation
                && runtime.lastSimulationMaintenanceTick == batch.gameTime) {
            runtime.lastSimulationMaintenanceTick = Long.MIN_VALUE;
        }
    }

    /** Exact revision-set comparison shared with focused stale-result tests. */
    static boolean revisionsMatch(
            Map<Long, Long> expectedRevisions,
            LongFunction<Long> currentRevision
    ) {
        Objects.requireNonNull(expectedRevisions, "Expected revisions are required");
        Objects.requireNonNull(currentRevision, "Current revision lookup is required");
        for (Map.Entry<Long, Long> expected : expectedRevisions.entrySet()) {
            Long current = currentRevision.apply(expected.getKey());
            if (!Objects.equals(expected.getValue(), current)) {
                return false;
            }
        }
        return true;
    }

    // Converts continuous cell physics into sparse observations while the
    // tracker owns identity, movement, merging, splitting, and dissipation.
    private static void collectObservations(
            List<WeatherSystemTracker.Observation> observations,
            WeatherSample sample,
            AtmosphericFrontModel.FrontState front,
            WeatherHazardModel.HazardProfile hazards,
            WeatherConfig.FeatureSettings features,
            double centerX,
            double centerZ,
            int cellSize
    ) {
        double stormIntensity = Math.min(1.0,
                sample.stormEnergy() * 0.50
                        + sample.precipitationIntensity() * 0.20
                        + sample.instability() * 0.16
                        + Math.max(0.0, sample.verticalMotion()) * 0.14
        );
        if (stormIntensity >= 0.15) {
            WeatherSystemType type = WeatherSystemType.STORM;
            if (features.severeWeatherEnabled()
                    && features.tornadoesEnabled()
                    && hazards.tornado() >= 0.68) {
                type = WeatherSystemType.TORNADO;
            } else if (features.severeWeatherEnabled()
                    && features.cyclonesEnabled()
                    && hazards.cyclone() >= 0.68) {
                type = WeatherSystemType.CYCLONE;
            }
            observations.add(new WeatherSystemTracker.Observation(
                    type,
                    centerX,
                    centerZ,
                    cellSize * (0.70 + stormIntensity * 1.20),
                    stormIntensity,
                    sample.cloudWind(),
                    Math.max(sample.windShear(), Math.max(hazards.tornado(), hazards.cyclone()))
            ));
        }
        if (front.strength() >= 0.15) {
            WeatherSystemType type = switch (front.type()) {
                case WARM -> WeatherSystemType.WARM_FRONT;
                case COLD -> WeatherSystemType.COLD_FRONT;
                case STATIONARY -> WeatherSystemType.STATIONARY_FRONT;
                case OCCLUDED -> WeatherSystemType.OCCLUDED_FRONT;
                case NONE -> null;
            };
            if (type != null) {
                observations.add(new WeatherSystemTracker.Observation(
                        type,
                        centerX,
                        centerZ,
                        cellSize * (0.90 + front.strength() * 1.60),
                        front.strength(),
                        new WindVector(
                                sample.cloudWind().x() + front.gust().x(),
                                sample.cloudWind().z() + front.gust().z()
                        ).limited(1.0),
                        Math.max(front.strength(), sample.windShear())
                ));
            }
        }
    }

    private void maintainVanillaCommandWeather(
            ServerLevel level,
            LevelRuntime runtime,
            AtmosphereSavedData data,
            WeatherConfig.SchedulingSettings scheduling,
            long gameTime
    ) {
        Set<Long> activeKeys = collectActiveKeys(level, scheduling);
        int changed = applyVanillaState(
                level,
                runtime,
                data,
                scheduling,
                activeKeys,
                runtime.vanillaWeatherState,
                gameTime,
                true
        );
        for (long packedKey : activeKeys) {
            data.grid().markActive(AtmosphereCellKey.fromPacked(packedKey), gameTime);
        }
        if (!activeKeys.isEmpty()) {
            data.markChanged();
        }
        if (changed > 0) {
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
    }

    private void expireVanillaCommandWeather(
            ServerLevel level,
            LevelRuntime runtime,
            AtmosphereSavedData data,
            long gameTime
    ) {
        if (runtime.vanillaWeatherState == null || gameTime < runtime.vanillaWeatherUntilTick) {
            return;
        }

        VanillaWeatherCommandAdapter.State expiredState = runtime.vanillaWeatherState;
        Set<Long> affectedKeys = Set.copyOf(runtime.vanillaWeatherAppliedKeys);
        runtime.clearVanillaWeather();
        if (expiredState == VanillaWeatherCommandAdapter.State.CLEAR) {
            return;
        }

        int changed = applyVanillaState(
                level,
                runtime,
                data,
                WeatherConfig.scheduling(),
                affectedKeys,
                VanillaWeatherCommandAdapter.State.CLEAR,
                gameTime,
                false
        );
        runtime.vanillaWeatherAppliedKeys.clear();
        if (changed > 0) {
            data.markChanged();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
    }

    private int applyVanillaState(
            ServerLevel level,
            LevelRuntime runtime,
            AtmosphereSavedData data,
            WeatherConfig.SchedulingSettings scheduling,
            Set<Long> targetKeys,
            VanillaWeatherCommandAdapter.State state,
            long gameTime,
            boolean createMissing
    ) {
        int changed = 0;
        for (long packedKey : targetKeys) {
            AtmosphereCellKey key = AtmosphereCellKey.fromPacked(packedKey);
            AtmosphereView view = data.grid().view(key);
            if (view == null && createMissing) {
                view = ensureCell(level, data, key, scheduling);
            }
            if (view == null) {
                continue;
            }

            boolean snowClimateEligible = state != VanillaWeatherCommandAdapter.State.CLEAR
                    && PrecipitationPhaseModel.supportsNaturalSnow(runtime.inputSampler.sample(
                            level,
                            key,
                            scheduling.cellSize(),
                            scheduling.environmentResampleIntervalTicks()
                    ));
            WeatherSample next = VanillaWeatherCommandAdapter.apply(
                    view.sample(),
                    state,
                    snowClimateEligible
            );
            if (!view.sample().equals(next) && data.grid().force(key, next, gameTime)) {
                changed++;
            }
            runtime.vanillaWeatherAppliedKeys.add(packedKey);
        }
        return changed;
    }

    private static void addExistingCardinalNeighbors(
            Set<Long> scheduledKeys,
            Map<Long, AtmosphereView> views,
            AtmosphereCellKey center
    ) {
        addIfPresent(scheduledKeys, views, center.x(), center.z() - 1);
        addIfPresent(scheduledKeys, views, center.x() + 1, center.z());
        addIfPresent(scheduledKeys, views, center.x(), center.z() + 1);
        addIfPresent(scheduledKeys, views, center.x() - 1, center.z());
    }

    private static void addIfPresent(
            Set<Long> scheduledKeys,
            Map<Long, AtmosphereView> views,
            int cellX,
            int cellZ
    ) {
        long packed = new AtmosphereCellKey(cellX, cellZ).packed();
        if (views.containsKey(packed)) {
            scheduledKeys.add(packed);
        }
    }

    private Set<Long> collectActiveKeys(
            ServerLevel level,
            WeatherConfig.SchedulingSettings scheduling
    ) {
        int radius = Math.min(16, scheduling.activeSimulationRadius() + 1);
        Set<Long> keys = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AtmosphereCellKey center = AtmosphereCellKey.fromBlock(
                    player.getBlockX(),
                    player.getBlockZ(),
                    scheduling.cellSize()
            );
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    keys.add(new AtmosphereCellKey(
                            center.x() + offsetX,
                            center.z() + offsetZ
                    ).packed());
                }
            }
        }
        return keys;
    }

    private AtmosphereSimulationEngine.Neighborhood neighborhood(
            Map<Long, AtmosphereView> views,
            AtmosphereView center
    ) {
        AtmosphereCellKey key = center.key();
        WeatherSample fallback = center.sample();
        return new AtmosphereSimulationEngine.Neighborhood(
                neighbor(views, key.x(), key.z() - 1, fallback),
                neighbor(views, key.x() + 1, key.z(), fallback),
                neighbor(views, key.x(), key.z() + 1, fallback),
                neighbor(views, key.x() - 1, key.z(), fallback)
        );
    }

    private static WeatherSample neighbor(
            Map<Long, AtmosphereView> views,
            int cellX,
            int cellZ,
            WeatherSample fallback
    ) {
        AtmosphereView view = views.get(new AtmosphereCellKey(cellX, cellZ).packed());
        return view == null ? fallback : view.sample();
    }

    private CellContext ensureCell(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return null;
        }
        WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
        AtmosphereSavedData data = data(level, scheduling);
        AtmosphereCellKey key = AtmosphereCellKey.fromBlock(
                position.getX(),
                position.getZ(),
                scheduling.cellSize()
        );
        AtmosphereView view = ensureCell(level, data, key, scheduling);
        return new CellContext(data, key, view);
    }

    private AtmosphereView ensureCell(
            ServerLevel level,
            AtmosphereSavedData data,
            AtmosphereCellKey key,
            WeatherConfig.SchedulingSettings scheduling
    ) {
        AtmosphereView existing = data.grid().view(key);
        if (existing != null) {
            return existing;
        }
        AtmosphereEnvironment environment = runtime(level).inputSampler.sample(
                level,
                key,
                scheduling.cellSize(),
                scheduling.environmentResampleIntervalTicks()
        );
        WeatherSample initial = initialSample(environment, WeatherConfig.settings());
        AtmosphereView created = data.grid().getOrCreate(key, initial, level.getGameTime());
        data.markChanged();
        return created;
    }

    private static WeatherSample initialSample(
            AtmosphereEnvironment environment,
            SimulationSettings settings
    ) {
        double temperature = environment.targetTemperatureCelsius(settings.randomVariation());
        double humidity = Math.max(0.05, Math.min(1.0,
                environment.biomeHumidity() * 0.78 + environment.waterCoverage() * 0.32));
        double pressure = Math.max(WeatherSample.MIN_PRESSURE, Math.min(WeatherSample.MAX_PRESSURE,
                1.0 - (temperature - 15.0) * 0.002 - (environment.elevationBlocks() - 64.0) * 0.00002));
        double cloudWater = Math.max(0.0,
                (humidity - settings.cloudFormationThreshold()) * 0.35);
        double instability = Math.max(0.08, Math.min(0.45,
                0.12 + Math.abs(temperature - 15.0) / 100.0));
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                WindVector.ZERO,
                cloudWater,
                instability,
                0.0,
                0.0,
                PrecipitationType.NONE
        );
    }

    private boolean forceContext(CellContext context, WeatherSample next, long gameTime) {
        boolean changed = context.data.grid().force(context.key, next, gameTime);
        if (changed) {
            context.data.markChanged();
            WeatherSnapshotManager.markLevelDirty(context.data.dimension());
        }
        return changed;
    }

    private static WeatherSample copy(
            WeatherSample old,
            double temperature,
            double humidity,
            double pressure,
            double cloudWater,
            double instability,
            double stormEnergy,
            double precipitationIntensity,
            PrecipitationType precipitationType
    ) {
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                old.wind(),
                cloudWater,
                instability,
                stormEnergy,
                precipitationIntensity,
                precipitationType,
                old.verticalMotion(),
                old.cloudDepth(),
                old.cloudWind(),
                old.surface()
        );
    }

    private LevelRuntime runtime(ServerLevel level) {
        return runtimes.computeIfAbsent(level, ignored -> {
            AtmosphereInputSampler inputSampler = new AtmosphereInputSampler(
                    new WildernessWeatherWaterInfluence(),
                    SeasonalWeatherIntegrations.discover()
            );
            return new LevelRuntime(
                    inputSampler,
                    new LocalizedLightningScheduler(this),
                    new WildfireScheduler(this, inputSampler)
            );
        });
    }

    private AtmosphereSavedData data(
            ServerLevel level,
            WeatherConfig.SchedulingSettings scheduling
    ) {
        return data(level, scheduling, runtime(level));
    }

    private AtmosphereSavedData data(
            ServerLevel level,
            WeatherConfig.SchedulingSettings scheduling,
            LevelRuntime runtime
    ) {
        AtmosphereSavedData data = AtmosphereSavedData.get(level, scheduling);
        int attachedCellSize = data.grid().cellSize();
        if (runtime.attachedCellSize >= 0 && runtime.attachedCellSize != attachedCellSize) {
            runtime.invalidateSimulationGeneration();
            WeatherSnapshotManager.markLevelDirty(level.dimension());
        }
        runtime.attachedCellSize = attachedCellSize;
        runtime.atmosphereGrid = data.grid();
        return data;
    }

    private AtmosphereGrid queryGrid(ServerLevel level) {
        LevelRuntime runtime = runtimes.get(level);
        AtmosphereGrid cached = runtime == null ? null : runtime.atmosphereGrid;
        if (cached != null) {
            return cached;
        }

        // Persisted weather must be visible before the first post-server tick.
        // This storage lookup occurs once per level; steady-state position
        // queries remain allocation-free through the attached runtime grid.
        LevelRuntime attachedRuntime = runtime == null ? runtime(level) : runtime;
        return data(level, WeatherConfig.scheduling(), attachedRuntime).grid();
    }

    private static int synchronizationRadius(WeatherConfig.SchedulingSettings scheduling) {
        return Math.min(
                WeatherRegionSyncPayload.MAX_CELL_OFFSET,
                Math.max(1, scheduling.activeSimulationRadius() + 1)
        );
    }

    private static boolean isDue(long gameTime, int interval) {
        return Math.floorMod(gameTime, Math.max(1, interval)) == 0L;
    }

    /** Modulo-free due check shared with focused cadence tests. */
    static boolean isElapsedDue(long currentTick, long lastTick, int intervalTicks) {
        return lastTick == Long.MIN_VALUE
                || currentTick < lastTick
                || currentTick - lastTick >= Math.max(1, intervalTicks);
    }

    /** Result returned to the performance adapter after one optional level pass. */
    public record OptionalMaintenanceResult(boolean simulationRan, boolean snapshotDue) {
    }

    /** Server-thread capture outcome consumed by the performance adapter. */
    public record OptionalMaintenancePreparation(
            boolean simulationDue,
            boolean snapshotDue,
            SimulationBatch simulationBatch
    ) {
    }

    /**
     * Opaque immutable input for one atmospheric calculation generation.
     *
     * <p>The object intentionally exposes only its size; worker code passes it
     * back to this authority instead of interpreting ownership state itself.</p>
     */
    public static final class SimulationBatch {
        private final long generation;
        private final long gameTime;
        private final int cellSize;
        private final int simulationIntervalTicks;
        private final int maximumPersistedCells;
        private final boolean debugLogging;
        private final SimulationSettings settings;
        private final WeatherConfig.FeatureSettings features;
        private final WeatherSystemTracker.TrackingSettings trackingSettings;
        private final Set<Long> activeKeys;
        private final Map<Long, Long> expectedRevisions;
        private final List<CellCalculationInput> inputs;

        private SimulationBatch(
                long generation,
                long gameTime,
                int cellSize,
                int simulationIntervalTicks,
                int maximumPersistedCells,
                boolean debugLogging,
                SimulationSettings settings,
                WeatherConfig.FeatureSettings features,
                WeatherSystemTracker.TrackingSettings trackingSettings,
                Set<Long> activeKeys,
                Map<Long, Long> expectedRevisions,
                List<CellCalculationInput> inputs
        ) {
            this.generation = generation;
            this.gameTime = gameTime;
            this.cellSize = cellSize;
            this.simulationIntervalTicks = simulationIntervalTicks;
            this.maximumPersistedCells = maximumPersistedCells;
            this.debugLogging = debugLogging;
            this.settings = settings;
            this.features = features;
            this.trackingSettings = trackingSettings;
            this.activeKeys = activeKeys;
            this.expectedRevisions = expectedRevisions;
            this.inputs = inputs;
        }

        /** Returns the number of detached cell calculations in this batch. */
        public int cellCount() {
            return inputs.size();
        }
    }

    /** Opaque immutable calculation output awaiting server-thread validation. */
    public static final class SimulationResult {
        private final SimulationBatch batch;
        private final List<CalculatedCell> calculated;
        private final List<WeatherSystemTracker.Observation> observations;

        private SimulationResult(
                SimulationBatch batch,
                List<CalculatedCell> calculated,
                List<WeatherSystemTracker.Observation> observations
        ) {
            this.batch = batch;
            this.calculated = calculated;
            this.observations = observations;
        }
    }

    /** Debug-editable atmospheric scalar. */
    public enum ControlField {
        TEMPERATURE,
        HUMIDITY,
        PRESSURE,
        STORM_ENERGY
    }

    /** Simulation scheduling state exposed only as diagnostics. */
    public enum Activity {
        ACTIVE,
        GRACE,
        PERSISTENT_STORM,
        DORMANT
    }

    private static final class LevelRuntime {
        private final AtmosphereInputSampler inputSampler;
        private final LocalizedLightningScheduler lightningScheduler;
        private final WildfireScheduler wildfireScheduler;
        private final SurfaceWeatheringScheduler surfaceWeatheringScheduler = new SurfaceWeatheringScheduler();
        private final SevereWeatherScheduler severeWeatherScheduler = new SevereWeatherScheduler();
        private final RegionalWeatherThreatCache approachingWeatherCache = new RegionalWeatherThreatCache();
        private final Set<Long> vanillaWeatherAppliedKeys = new HashSet<>();
        private volatile AtmosphereGrid atmosphereGrid;
        private VanillaWeatherCommandAdapter.State vanillaWeatherState;
        private long vanillaWeatherUntilTick;
        private long lastSimulationMaintenanceTick = Long.MIN_VALUE;
        private long lastSnapshotRequestTick = Long.MIN_VALUE;
        private long simulationGeneration = 1L;
        private int attachedCellSize = -1;

        private LevelRuntime(
                AtmosphereInputSampler inputSampler,
                LocalizedLightningScheduler lightningScheduler,
                WildfireScheduler wildfireScheduler
        ) {
            this.inputSampler = inputSampler;
            this.lightningScheduler = lightningScheduler;
            this.wildfireScheduler = wildfireScheduler;
        }

        private void beginVanillaWeather(
                VanillaWeatherCommandAdapter.State state,
                long gameTime,
                int durationTicks
        ) {
            long safeDuration = Math.max(1L, durationTicks);
            vanillaWeatherState = state;
            vanillaWeatherUntilTick = gameTime > Long.MAX_VALUE - safeDuration
                    ? Long.MAX_VALUE
                    : gameTime + safeDuration;
            vanillaWeatherAppliedKeys.clear();
        }

        private void clearVanillaWeather() {
            vanillaWeatherState = null;
            vanillaWeatherUntilTick = 0L;
            vanillaWeatherAppliedKeys.clear();
        }

        private void invalidateSimulationGeneration() {
            simulationGeneration = simulationGeneration == Long.MAX_VALUE
                    ? 1L
                    : simulationGeneration + 1L;
        }
    }

    private record CellCalculationInput(
            AtmosphereCellKey key,
            long baseRevision,
            WeatherSample sample,
            AtmosphereEnvironment environment,
            AtmosphereSimulationEngine.Neighborhood neighborhood,
            int catchUpSteps,
            WeatherSystemTracker.SystemInfluence systemInfluence,
            double centerX,
            double centerZ
    ) {
    }

    private record CalculatedCell(
            AtmosphereCellKey key,
            long baseRevision,
            WeatherSample sample
    ) {
    }

    private record CellContext(
            AtmosphereSavedData data,
            AtmosphereCellKey key,
            AtmosphereView view
    ) {
    }
}
