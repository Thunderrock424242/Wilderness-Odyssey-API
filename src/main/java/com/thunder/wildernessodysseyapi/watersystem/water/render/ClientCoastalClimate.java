package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.environment.glacial.client.ClientGlacialState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSeasonModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Client-only adapter from synchronized weather/glacial state to the pure coastal season model. */
final class ClientCoastalClimate {

    private ClientCoastalClimate() {
    }

    /** Returns a neutral sample when the presentation toggle is disabled. */
    static CoastalSeasonModel.Sample sample(ClientLevel level, CoastalSegment segment) {
        if (!WaterRenderingConfig.coastalSeasonInfluenceEnabled()) {
            return CoastalSeasonModel.NEUTRAL;
        }
        BlockPos position = new BlockPos(
                segment.centerX(), (int) Math.floor(segment.surfaceY()), segment.centerZ());
        WeatherSample weather = ClientWeatherCoordinator.sampleAt(level, position);
        double melt = segment.profile().shoreType() == CoastalWaveProfile.ShoreType.GLACIAL
                ? ClientGlacialState.snapshot(level).meltFraction()
                : 0.5;
        return CoastalSeasonModel.sample(
                segment.profile().shoreType(),
                weather.temperature(),
                weather.surface().snowpack(),
                weather.surface().frozenFraction(),
                melt
        );
    }
}
