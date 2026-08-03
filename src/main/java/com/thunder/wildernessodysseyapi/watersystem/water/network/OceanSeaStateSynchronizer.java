package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaStateField;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** Publishes player-relevant regional weather-driven sea state. */
public final class OceanSeaStateSynchronizer {

    private OceanSeaStateSynchronizer() {
    }

    /** Sends each player the bounded sea-state lattice surrounding them. */
    public static void syncLevel(ServerLevel level) {
        for (var player : level.players()) {
            OceanSeaStatePayload payload;
            if (WaterSimulationConfig.weatherWaterCouplingEnabled()
                    && WeatherConfig.dimensionEnabled(level.dimension())) {
                payload = OceanSeaStatePayload.regional(
                        WaterSimulationConfig.seaStateCellSize(),
                        OceanSeaStateField.cellsAround(
                                level,
                                player.getBlockX(),
                                player.getBlockZ()
                        )
                );
            } else {
                payload = OceanSeaStatePayload.disabled(
                        WaterSimulationConfig.seaStateCellSize()
                );
            }
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
