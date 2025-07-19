package com.thunder.wildernessodysseyapi.ModListTracker;

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
        ModTracker.checkModChanges();
    }
}
