package com.thunder.wildernessodysseyapi.ecosystem.debug;

import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import com.thunder.wildernessodysseyapi.ecosystem.network.EnvironmentalMemoryDebugPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends one bounded, debug-only environmental-memory snapshot to a player. */
public final class EnvironmentalMemoryDebugSync {

    private EnvironmentalMemoryDebugSync() {
    }

    /** Samples only the player's current cell and sends it through the existing play channel. */
    public static void send(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos cell = player.chunkPosition();
        PacketDistributor.sendToPlayer(player, EnvironmentalMemoryDebugPayload.create(
                level.dimension().location(),
                cell,
                EnvironmentalMemoryManager.getMemory(level, player.blockPosition()),
                level.getGameTime(),
                EnvironmentalMemoryManager.getActiveCellCount(level)
        ));
    }
}
