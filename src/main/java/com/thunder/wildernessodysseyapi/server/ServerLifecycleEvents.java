package com.thunder.wildernessodysseyapi.server;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.faq.FaqReloadListener;
import com.thunder.wildernessodysseyapi.gamerules.GameRulesListManager;
import com.thunder.wildernessodysseyapi.modpack.structure.ModpackStructureRegistry;
import com.thunder.wildernessodysseyapi.ownership.config.OwnershipConfig;
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
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RiftfallSystem.tick(level);
        }
    }

    /** Stops worker threads when the dedicated or integrated server shuts down. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AsyncTaskManager.shutdown();
    }

    /** Clears world-derived caches when a level is unloaded to avoid retaining stale state. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        WaterBodyClassifier.clearCache();
        BoatTiltStore.clear();
        SPHSimulationManager.get().shutdown();
    }

    /** Adds the FAQ data listener to each server resource reload. */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FaqReloadListener());
    }
}
