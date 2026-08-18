package com.thunder.wildernessodysseyapi.weather.api;

import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.wind.WindFieldModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Unified public facade for lightweight regional wind queries.
 *
 * <p>Server calls read the authoritative atmosphere grid. Client calls read
 * the already synchronized and interpolated weather region plus its captured
 * wind settings. No query loads chunks, allocates persistent state, or sends a
 * network packet.</p>
 */
public final class WindManager {

    private WindManager() {
    }

    /** Returns wind at the center of a block on either logical side. */
    public static WindSample getWind(Level level, BlockPos position) {
        if (position == null) {
            return WindSample.calm(new AtmosphereCellKey(0, 0));
        }
        return getWind(level, Vec3.atCenterOf(position));
    }

    /** Returns wind at an exact world position on either logical side. */
    public static WindSample getWind(Level level, Vec3 position) {
        if (level == null || position == null) {
            return WindSample.calm(new AtmosphereCellKey(0, 0));
        }

        WeatherSample weather;
        WindSettings settings;
        int cellSize;
        if (level instanceof ServerLevel serverLevel) {
            WeatherConfig.SchedulingSettings scheduling = WeatherConfig.scheduling();
            cellSize = scheduling.cellSize();
            if (!WeatherConfig.dimensionEnabled(serverLevel.dimension())) {
                return calmAt(position, cellSize);
            }
            weather = WeatherServices.query().sample(serverLevel, BlockPos.containing(position));
            settings = WeatherConfig.windSettings();
        } else if (level instanceof ClientLevel clientLevel
                && ClientWeatherCoordinator.controls(clientLevel)) {
            ClientWeatherCoordinator.ClientStateView state = ClientWeatherCoordinator.stateView(clientLevel);
            if (state == null) {
                return WindSample.calm(new AtmosphereCellKey(0, 0));
            }
            cellSize = state.cellSize();
            weather = ClientWeatherCoordinator.sampleAt(clientLevel, position);
            settings = ClientWeatherCoordinator.windSettings(clientLevel);
        } else {
            return WindSample.calm(new AtmosphereCellKey(0, 0));
        }

        return WindFieldModel.sample(
                weather,
                settings,
                cellSize,
                position.x,
                position.z,
                level.getGameTime(),
                WindFieldModel.dimensionSalt(level.dimension().location())
        );
    }

    /** Convenience overload for callers that already hold scalar coordinates. */
    public static WindSample getWind(Level level, double x, double y, double z) {
        return getWind(level, new Vec3(x, y, z));
    }

    private static WindSample calmAt(Vec3 position, int cellSize) {
        return WindSample.calm(AtmosphereCellKey.fromBlock(
                (int) Math.floor(position.x),
                (int) Math.floor(position.z),
                cellSize
        ));
    }
}
