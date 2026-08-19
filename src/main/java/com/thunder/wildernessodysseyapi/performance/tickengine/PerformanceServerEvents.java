package com.thunder.wildernessodysseyapi.performance.tickengine;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Places Tick Engine measurement around the ordinary NeoForge server-tick lifecycle.
 *
 * <p>The pre handler starts first and the post handler finishes last, so existing
 * WO event handlers remain in their established order. No mixin is required.</p>
 */
public final class PerformanceServerEvents {
    private PerformanceServerEvents() {
    }

    /** Starts monotonic measurement before normal server tick work. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        TickEngine.beginServerTick(System.nanoTime());
    }

    /** Runs optional WO queues and closes measurement after existing post-tick handlers. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickPost(ServerTickEvent.Post event) {
        TickEngine.finishServerTick(event.getServer(), event::hasTime);
    }
}
