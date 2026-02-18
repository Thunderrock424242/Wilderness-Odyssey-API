package com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber
/**
 * Hooks server events to run mod tracking logic.
 */
public class ModEventHandler {
    /**
     * Invoked when the server starts to check for mod list changes.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting detected; beginning mod list change check.");
        ModTracker.checkModChanges();
        LOGGER.info("Mod list change check finished.");
    }
}
