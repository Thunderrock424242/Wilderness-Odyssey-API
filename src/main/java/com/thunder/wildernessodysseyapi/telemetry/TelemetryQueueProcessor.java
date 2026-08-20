package com.thunder.wildernessodysseyapi.telemetry;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;

/**
 * Flushes queued telemetry payloads on a schedule using the background IO thread pool.
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
            clearCachesAfterDisable();
            return;
        }
        PlayerTelemetryConfig.TelemetryConfigValues playerConfig = PlayerTelemetryConfig.values();
        EventTelemetryConfig.EventTelemetryValues eventConfig = EventTelemetryConfig.values();
        boolean playerExporterConfigured = playerConfig.enabled()
                && playerConfig.sheetWebhookUrl() != null
                && !playerConfig.sheetWebhookUrl().isBlank();
        boolean eventExporterConfigured = eventConfig.enabled()
                && eventConfig.webhookUrl() != null
                && !eventConfig.webhookUrl().isBlank();
        if (!playerExporterConfigured && !eventExporterConfigured) {
            clearCachesAfterDisable();
            return;
        }
        int interval = Math.max(1, config.queueFlushIntervalTicks());
        if (event.getServer().getTickCount() % interval != 0) {
            return;
        }

        if (playerExporterConfigured) {
            PlayerTelemetryReporter.evictExpiredCaches(playerConfig, Instant.now());
        }

        // Each server-owned queue prevents its own overlapping network flush.
        TelemetryQueue queue = TelemetryQueue.get(event.getServer());
        if (!queue.tryBeginFlush()) {
            return;
        }
        int batchSize = config.queueFlushBatchSize();
        if (!AsyncTaskManager.trySubmitIoWork("Telemetry_Flush", () -> {
            try {
                queue.flush(batchSize);
            } finally {
                queue.finishFlush();
            }
        })) {
            queue.finishFlush();
        }
    }

    private static void clearCachesAfterDisable() {
        PlayerTelemetryReporter.clearCaches();
    }
}
