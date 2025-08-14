package com.thunder.wildernessodysseyapi.MemUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Records memory usage on the server thread every tick so modpack authors can
 * gauge peak memory requirements during play.
 */
@EventBusSubscriber(modid = MOD_ID)
public class MemoryTracker {

    /**
     * Invoked each server tick; updates peak memory usage statistics.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MemoryUtils.recordPeakUsage();
    }
}
