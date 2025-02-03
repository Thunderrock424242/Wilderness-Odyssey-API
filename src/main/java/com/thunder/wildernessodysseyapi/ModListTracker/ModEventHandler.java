package com.thunder.wildernessodysseyapi.ModListTracker;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber
public class ModEventHandler {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ModTracker.checkModChanges();
    }
}
