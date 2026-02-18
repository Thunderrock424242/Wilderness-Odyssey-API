package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Event hooks for the world upgrade system.
 */
@EventBusSubscriber(modid = MOD_ID)
public final class WorldUpgradeEvents {
    private WorldUpgradeEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        WorldUpgradeManager.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        WorldUpgradeManager.onChunkLoad(level, chunk);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) {
            return;
        }
        WorldUpgradeManager.runTick(event.getServer());
    }
}
