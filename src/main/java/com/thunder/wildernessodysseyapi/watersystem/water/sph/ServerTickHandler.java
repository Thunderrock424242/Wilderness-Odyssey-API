package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.network.SphSnapshotSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.network.OceanSeaStateSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.network.WaterVolumeSynchronizer;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * ServerTickHandler
 *
 * Advances, synchronizes, and persists server-owned SPH water each tick.
 * Mobile bucket volume remains SPH until it settles into canonical chunk cells.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public class ServerTickHandler {

    private static final float SERVER_TICK_DELTA = 0.05f;
    private static int ticksUntilSnapshot = SPHConstants.NETWORK_SNAPSHOT_INTERVAL_TICKS;
    private static int ticksUntilPersistence = SPHConstants.PERSISTENCE_CAPTURE_INTERVAL_TICKS;
    private static int ticksUntilVolumeSnapshot = 10;
    private static int ticksUntilSeaStateSnapshot = 20;

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
        boolean publishVolumeSnapshot = --ticksUntilVolumeSnapshot <= 0;
        if (publishVolumeSnapshot) {
            ticksUntilVolumeSnapshot = 10;
        }
        boolean publishSeaStateSnapshot = --ticksUntilSeaStateSnapshot <= 0;
        if (publishSeaStateSnapshot) {
            ticksUntilSeaStateSnapshot = 20;
        }

        for (var level : event.getServer().getAllLevels()) {
            SPHSimulationManager manager = SPHSimulationManager.get();
            manager.ensurePersistentLevelLoaded(level);
            manager.tickLevel(level, SERVER_TICK_DELTA);
            if (publishSnapshot) {
                SphSnapshotSynchronizer.syncLevel(level);
            }
            if (publishVolumeSnapshot) {
                WaterVolumeSynchronizer.syncLevel(level);
            }
            if (publishSeaStateSnapshot) {
                OceanSeaStateSynchronizer.syncLevel(level);
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
            CanonicalWater.clearLevel(serverLevel);
        }
    }
}
