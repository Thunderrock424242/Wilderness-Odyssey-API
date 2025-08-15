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

    private static final int SAMPLE_INTERVAL = 20;
    private static int tickCounter = 0;

    /**
     * Invoked on server ticks; periodically updates peak memory statistics.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter >= SAMPLE_INTERVAL) {
            tickCounter = 0;
            MemoryUtils.recordPeakUsage();
        }
    }
}
