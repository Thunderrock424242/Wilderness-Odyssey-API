package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** Publishes the dimension's authoritative weather-driven sea state. */
public final class OceanSeaStateSynchronizer {

    private OceanSeaStateSynchronizer() {
    }

    /** Sends one compact snapshot to every player currently in the level. */
    public static void syncLevel(ServerLevel level) {
        OceanSeaStatePayload payload = OceanSeaStatePayload.fromSample(
                OceanSeaState.sample(level, 0.0f)
        );
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
