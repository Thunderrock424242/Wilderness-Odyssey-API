package com.thunder.wildernessodysseyapi.watersystem.ocean;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns bounded, regional, weather-driven sea-state memory on the server.
 *
 * <p>Cells sample the public {@code WeatherQuery} at their centers and retain
 * only wave response—not weather or water volume. The field never loads chunks,
 * and old entries expire before the hard configured bound is enforced.</p>
 */
public final class OceanSeaStateField {

    private static final long CELL_EXPIRY_TICKS = 12_000L;
    private static final long MAX_RESPONSE_STEP_TICKS = 200L;
    private static final Map<ServerLevel, LevelField> LEVELS = new IdentityHashMap<>();

    private OceanSeaStateField() {
    }

    /** Advances player-relevant regional cells on the logical server thread. */
    public static void tickLevel(ServerLevel level) {
        if (level == null || !WaterSimulationConfig.weatherWaterCouplingEnabled()) {
            return;
        }
        if (!WeatherConfig.dimensionEnabled(level.dimension())) {
            clearLevel(level);
            return;
        }
        long gameTime = level.getGameTime();
        int interval = WaterSimulationConfig.seaStateUpdateIntervalTicks();
        if (Math.floorMod(gameTime, interval) != 0L) {
            return;
        }

        LevelField field = LEVELS.computeIfAbsent(level, ignored -> new LevelField());
        int cellSize = WaterSimulationConfig.seaStateCellSize();
        int radius = WaterSimulationConfig.seaStateSyncRadiusCells();
        for (var player : level.players()) {
            AtmosphereCellKey center = AtmosphereCellKey.fromBlock(
                    player.getBlockX(), player.getBlockZ(), cellSize);
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                    field.update(level, new AtmosphereCellKey(
                            center.x() + offsetX,
                            center.z() + offsetZ
                    ), cellSize, gameTime);
                }
            }
        }
        field.trim(gameTime, WaterSimulationConfig.seaStateMaxCells());
    }

    /** Returns a spatially interpolated server sea state at world coordinates. */
    public static OceanSeaState.Sample sampleAt(
            ServerLevel level,
            double worldX,
            double worldZ,
            float partialTick
    ) {
        if (level == null || !WaterSimulationConfig.weatherWaterCouplingEnabled()
                || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return level == null
                    ? OceanSeaState.CALM
                    : OceanSeaState.vanillaFallback(level, partialTick);
        }
        LevelField field = LEVELS.computeIfAbsent(level, ignored -> new LevelField());
        return field.sample(level, worldX, worldZ,
                WaterSimulationConfig.seaStateCellSize(), level.getGameTime());
    }

    /** Returns the bounded square of cells synchronized to one player. */
    public static List<CellView> cellsAround(ServerLevel level, int blockX, int blockZ) {
        if (level == null || !WaterSimulationConfig.weatherWaterCouplingEnabled()
                || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return List.of();
        }
        int cellSize = WaterSimulationConfig.seaStateCellSize();
        int radius = WaterSimulationConfig.seaStateSyncRadiusCells();
        long gameTime = level.getGameTime();
        AtmosphereCellKey center = AtmosphereCellKey.fromBlock(blockX, blockZ, cellSize);
        LevelField field = LEVELS.computeIfAbsent(level, ignored -> new LevelField());
        List<CellView> views = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                AtmosphereCellKey key = new AtmosphereCellKey(
                        center.x() + offsetX,
                        center.z() + offsetZ
                );
                OceanSeaState.Sample sample = field.update(level, key, cellSize, gameTime);
                views.add(new CellView(key.x(), key.z(), sample));
            }
        }
        field.trim(gameTime, WaterSimulationConfig.seaStateMaxCells());
        return List.copyOf(views);
    }

    /** Releases ephemeral response memory when a dimension unloads. */
    public static void clearLevel(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** Immutable regional view used by the network boundary. */
    public record CellView(int cellX, int cellZ, OceanSeaState.Sample sample) {
    }

    private static final class LevelField {
        private final LinkedHashMap<Long, CellState> cells =
                new LinkedHashMap<>(128, 0.75f, true);

        private OceanSeaState.Sample sample(
                ServerLevel level,
                double worldX,
                double worldZ,
                int cellSize,
                long gameTime
        ) {
            double gridX = worldX / cellSize - 0.5;
            double gridZ = worldZ / cellSize - 0.5;
            int minimumX = floorToInt(gridX);
            int minimumZ = floorToInt(gridZ);
            float blendX = (float) (gridX - minimumX);
            float blendZ = (float) (gridZ - minimumZ);
            OceanSeaState.Sample northWest = update(
                    level, new AtmosphereCellKey(minimumX, minimumZ), cellSize, gameTime);
            OceanSeaState.Sample northEast = update(
                    level, new AtmosphereCellKey(minimumX + 1, minimumZ), cellSize, gameTime);
            OceanSeaState.Sample southWest = update(
                    level, new AtmosphereCellKey(minimumX, minimumZ + 1), cellSize, gameTime);
            OceanSeaState.Sample southEast = update(
                    level, new AtmosphereCellKey(minimumX + 1, minimumZ + 1), cellSize, gameTime);
            OceanSeaState.Sample north = northWest.interpolate(northEast, blendX);
            OceanSeaState.Sample south = southWest.interpolate(southEast, blendX);
            return north.interpolate(south, blendZ);
        }

        private OceanSeaState.Sample update(
                ServerLevel level,
                AtmosphereCellKey key,
                int cellSize,
                long gameTime
        ) {
            long packed = key.packed();
            CellState state = cells.get(packed);
            if (state != null
                    && gameTime - state.lastUpdatedTick
                    < WaterSimulationConfig.seaStateUpdateIntervalTicks()) {
                state.lastSeenTick = gameTime;
                return state.current;
            }

            BlockPos center = new BlockPos(
                    key.centerBlockX(cellSize),
                    level.getSeaLevel(),
                    key.centerBlockZ(cellSize)
            );
            WeatherSample weather = WeatherServices.query().sample(level, center);
            OceanSeaState.Sample previous = state == null ? OceanSeaState.CALM : state.current;
            OceanSeaState.Sample target = OceanSeaState.targetFromWeather(weather, previous);
            if (state == null) {
                // New cells start from calm response rather than snapping to a
                // storm target. The sync radius creates cells ahead of players,
                // giving them time to build before the boundary is crossed.
                state = new CellState(OceanSeaState.CALM, gameTime, gameTime);
                cells.put(packed, state);
                return state.current;
            }

            long elapsed = Math.max(1L, Math.min(
                    MAX_RESPONSE_STEP_TICKS,
                    gameTime - state.lastUpdatedTick
            ));
            state.current = state.current.approach(
                    target,
                    elapsed,
                    WaterSimulationConfig.seaStateBuildTimeSeconds(),
                    WaterSimulationConfig.seaStateDecayTimeSeconds()
            );
            state.lastUpdatedTick = gameTime;
            state.lastSeenTick = gameTime;
            return state.current;
        }

        private void trim(long gameTime, int maximumCells) {
            Iterator<Map.Entry<Long, CellState>> iterator = cells.entrySet().iterator();
            while (iterator.hasNext()) {
                if (gameTime - iterator.next().getValue().lastSeenTick > CELL_EXPIRY_TICKS) {
                    iterator.remove();
                }
            }
            while (cells.size() > Math.max(1, maximumCells)) {
                Iterator<Long> keys = cells.keySet().iterator();
                keys.next();
                keys.remove();
            }
        }
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static final class CellState {
        private OceanSeaState.Sample current;
        private long lastUpdatedTick;
        private long lastSeenTick;

        private CellState(OceanSeaState.Sample current, long lastUpdatedTick, long lastSeenTick) {
            this.current = current;
            this.lastUpdatedTick = lastUpdatedTick;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
