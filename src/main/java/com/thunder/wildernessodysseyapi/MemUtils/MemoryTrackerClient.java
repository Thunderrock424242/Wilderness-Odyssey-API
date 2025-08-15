package com.thunder.wildernessodysseyapi.MemUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Client-side hook for tracking peak memory usage while the game is running in
 * singleplayer or with an integrated server.
 */
@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class MemoryTrackerClient {

    private static final int SAMPLE_INTERVAL = 20;
    private static int tickCounter = 0;

    /**
     * Invoked on client ticks; periodically updates peak memory statistics.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter >= SAMPLE_INTERVAL) {
            tickCounter = 0;
            MemoryUtils.recordPeakUsage();
        }
    }
}
