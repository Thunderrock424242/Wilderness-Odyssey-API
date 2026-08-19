package com.thunder.wildernessodysseyapi.server;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileReloadListener;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.faq.FaqReloadListener;
import com.thunder.wildernessodysseyapi.gamerules.GameRulesListManager;
import com.thunder.wildernessodysseyapi.modpack.structure.ModpackStructureRegistry;
import com.thunder.wildernessodysseyapi.ownership.config.OwnershipConfig;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundEfficiencyManager;
import com.thunder.wildernessodysseyapi.performance.background.config.BackgroundEfficiencyConfig;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.config.TickEngineConfig;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatTiltStore;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Coordinates server lifecycle work shared by multiple gameplay systems.
 *
 * <p>All mutable world and gameplay state remains server-owned. Client-only
 * rendering and input handlers are registered elsewhere.</p>
 */
public final class ServerLifecycleEvents {

    private ServerLifecycleEvents() {
    }

    /** Initializes server-owned services and reloadable data before players join. */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        BackgroundEfficiencyManager.start(BackgroundEfficiencyConfig.values());
        TickEngine.start(TickEngineConfig.values(), BackgroundEfficiencyManager.schedulerControl());
        DataEngine.get().start(event.getServer());
        ServerPropertiesTemplateManager.ensureManagedServerProperties(event.getServer());
        GameRulesListManager.ensureRulesFileExists(event.getServer());
        GameRulesListManager.applyConfiguredRules(event.getServer());
        ModpackStructureRegistry.loadAll();

        if (OwnershipConfig.CONFIG.showNoticeOnStartup()) {
            LOGGER.info("[Ownership] Project: {}", OwnershipConfig.CONFIG.projectName());
            LOGGER.info("[Ownership] Owner: {}", OwnershipConfig.CONFIG.ownerName());
            LOGGER.info("[Ownership] Notice: {}", OwnershipConfig.CONFIG.ownershipNotice());
            LOGGER.info("[Ownership] Contact: {}", OwnershipConfig.CONFIG.supportContact());
        }
    }

    /** Drains main-thread work and advances Riftfall once per eligible server tick. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }

        AsyncTaskManager.drainMainThreadQueue(event.getServer());
        DataEngine.get().tick(event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RiftfallSystem.tick(level);
        }
    }

    /** Persists mobile water and stops runtime services during shutdown. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SPHSimulationManager waterManager = SPHSimulationManager.get();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            waterManager.capturePersistentLevel(level);
        }
        waterManager.shutdown();
        TickEngine.shutdown();
        BackgroundEfficiencyManager.shutdown();
        DataEngine.get().shutdown();
        AsyncTaskManager.shutdown();
    }

    /** Clears world-derived caches when a level is unloaded to avoid retaining stale state. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        WaterBodyClassifier.clearCache();
        BoatTiltStore.clear();
        // ServerTickHandler and ClientTickHandler release only the unloading
        // level. A global shutdown here used to erase water in other dimensions.
    }

    /** Adds the FAQ data listener to each server resource reload. */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FaqReloadListener());
        event.addListener(new SpeciesBehaviorProfileReloadListener());
    }
}
