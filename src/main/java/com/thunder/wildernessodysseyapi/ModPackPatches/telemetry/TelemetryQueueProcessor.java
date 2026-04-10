package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flushes queued telemetry payloads on a schedule using the background IO thread pool.
 */
public final class TelemetryQueueProcessor {

    // Prevents overlapping flushes if the network is extremely slow
    private static final AtomicBoolean IS_FLUSHING = new AtomicBoolean(false);

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

        // THE FIX: Offload the flush to the async IO pool and ensure only one runs at a time
        if (IS_FLUSHING.compareAndSet(false, true)) {
            TelemetryQueue queue = TelemetryQueue.get(event.getServer());
            int batchSize = config.queueFlushBatchSize();

            AsyncTaskManager.submitIoTask("Telemetry_Flush", () -> {
                try {
                    queue.flush(batchSize);
                } finally {
                    IS_FLUSHING.set(false); // Release the lock so the next interval can run
                }
                return java.util.Optional.empty();
            });
        }
    }
}