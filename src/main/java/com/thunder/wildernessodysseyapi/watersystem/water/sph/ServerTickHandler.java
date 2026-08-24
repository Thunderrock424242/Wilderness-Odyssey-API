package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.integration.WaterPerformanceIntegration;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * ServerTickHandler
 *
 * Advances gameplay-critical server-owned local SPH water each tick.
 * Buckets become canonical immediately; SPH is reserved for tiny active effects
 * such as falling canonical slices that later settle back into chunk cells.
 * Optional synchronization and periodic persistence are scheduled separately
 * by the Water Data Engine integration.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public class ServerTickHandler {

    private static final float SERVER_TICK_DELTA = 0.05f;
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) return;

        SPHSimulationManager manager = SPHSimulationManager.get();
        for (var level : event.getServer().getAllLevels()) {
            if (!WildernessWaterRules.isEnabled(level)) {
                manager.clearLevel(level);
                continue;
            }
            manager.ensurePersistentLevelLoaded(level);
            TickEngine.metrics().time(
                    "water",
                    () -> manager.tickLevel(level, SERVER_TICK_DELTA),
                    event.getServer().getTickCount()
            );
        }
    }

    /**
     * Drops runtime references when Minecraft unloads a server dimension.
     * SavedData remains owned by the level storage and is not discarded here.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SPHSimulationManager manager = SPHSimulationManager.get();
            // Capture the final mobile state before dropping this dimension's
            // runtime references; the periodic capture may be several ticks old.
            manager.capturePersistentLevel(serverLevel);
            manager.clearLevel(serverLevel);
            WaterPerformanceIntegration.forgetLevel(serverLevel);
            CanonicalWater.clearLevel(serverLevel);
        }
    }
}
