package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Flushes queued telemetry payloads on a schedule.
 */
public final class TelemetryQueueProcessor {
    private TelemetryQueueProcessor() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) {
            return;
        }
        TelemetryConfig.TelemetryValues config = TelemetryConfig.values();
        if (!config.enabled()) {
            return;
        }
        int interval = Math.max(1, config.queueFlushIntervalTicks());
        if (event.getServer().getTickCount() % interval != 0) {
            return;
        }
        TelemetryQueue queue = TelemetryQueue.get(event.getServer());
        queue.flush(config.queueFlushBatchSize());
    }
}
