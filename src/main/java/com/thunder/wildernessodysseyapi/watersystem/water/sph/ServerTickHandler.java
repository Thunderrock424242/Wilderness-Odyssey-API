package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.network.SphSnapshotSynchronizer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * ServerTickHandler
 *
 * Advances, synchronizes, and persists server-owned SPH water each tick.
 * Vanilla bucket sources remain the gameplay fallback while settled SPH
 * particles keep their volumetric render body.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public class ServerTickHandler {

    private static final float SERVER_TICK_DELTA = 0.05f;
    private static int ticksUntilSnapshot = SPHConstants.NETWORK_SNAPSHOT_INTERVAL_TICKS;
    private static int ticksUntilPersistence = SPHConstants.PERSISTENCE_CAPTURE_INTERVAL_TICKS;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) return;

        boolean publishSnapshot = --ticksUntilSnapshot <= 0;
        if (publishSnapshot) {
            ticksUntilSnapshot = SPHConstants.NETWORK_SNAPSHOT_INTERVAL_TICKS;
        }
        boolean capturePersistence = --ticksUntilPersistence <= 0;
        if (capturePersistence) {
            ticksUntilPersistence = SPHConstants.PERSISTENCE_CAPTURE_INTERVAL_TICKS;
        }

        for (var level : event.getServer().getAllLevels()) {
            SPHSimulationManager manager = SPHSimulationManager.get();
            manager.ensurePersistentLevelLoaded(level);
            manager.tickLevel(level, SERVER_TICK_DELTA);
            if (publishSnapshot) {
                SphSnapshotSynchronizer.syncLevel(level);
            }
            if (capturePersistence) {
                manager.capturePersistentLevel(level);
            }
        }
    }

    /**
     * Drops runtime references when Minecraft unloads a server dimension.
     * SavedData remains owned by the level storage and is not discarded here.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SPHSimulationManager.get().clearLevel(serverLevel);
        }
    }
}
