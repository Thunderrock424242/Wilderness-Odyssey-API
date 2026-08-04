package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedChunkState;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends compact immutable watershed windows around each player at a slow cadence.
 */
public final class WatershedSynchronizer {

    private WatershedSynchronizer() {
    }

    /** Sends loaded-only chunk conditions or an explicit disabled-state clear. */
    public static void syncLevel(ServerLevel level) {
        if (!WaterSimulationConfig.watershedSimulationEnabled()) {
            for (var player : level.players()) {
                PacketDistributor.sendToPlayer(player, WatershedRegionSyncPayload.disabled());
            }
            return;
        }
        WatershedSavedData data = WatershedSavedData.get(level);
        int radius = WaterSimulationConfig.watershedSimulationDistanceChunks();
        for (var player : level.players()) {
            List<WatershedRegionSyncPayload.ChunkSnapshot> chunks = new ArrayList<>();
            int centerX = player.getBlockX() >> 4;
            int centerZ = player.getBlockZ() >> 4;
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                    int chunkX = centerX + offsetX;
                    int chunkZ = centerZ + offsetZ;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    WatershedChunkState state = data.getOrCreate(level, chunk);
                    chunks.add(new WatershedRegionSyncPayload.ChunkSnapshot(
                            chunkX,
                            chunkZ,
                            state.packed()
                    ));
                }
            }
            PacketDistributor.sendToPlayer(
                    player,
                    new WatershedRegionSyncPayload(true, chunks)
            );
        }
    }
}
