package com.thunder.wildernessodysseyapi.dataengine.debug.client;

import com.thunder.wildernessodysseyapi.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.dataengine.debug.DataEngineDebugSnapshot;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Existing debug-HUD page backed only by measured, operator-subscribed server data. */
public final class DataEngineDebugPage implements DebugPage {
    public static final ResourceLocation ID = DataEngineIds.DEBUG_METRICS;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "WO DATA ENGINE";
    }

    @Override
    public List<DebugSection> sections(DebugContext context) {
        Optional<DataEngineDebugSnapshot> current = DataEngineDebugClientState.current();
        if (current.isEmpty()) {
            return List.of(DebugSection.builder("SERVER METRICS")
                    .add("State", DebugValue.unavailable("Waiting for operator-only server sync"))
                    .addRaw("Open this page as a permission-level 2 operator.")
                    .build());
        }

        DataEngineDebugSnapshot snapshot = current.get();
        return List.of(
                DebugSection.builder("TICK WORK")
                        .add("Main apply", milliseconds(snapshot.lastMainThreadNanos()))
                        .add("Tick budget", milliseconds(snapshot.tickBudgetNanos()))
                        .add("Backpressure", snapshot.backpressureActive()
                                ? DebugValue.warning("ACTIVE")
                                : DebugValue.good("INACTIVE"))
                        .build(),
                DebugSection.builder("UPDATES")
                        .add("Dirty", snapshot.dirtyEntries())
                        .add("Queued", snapshot.queuedWork())
                        .add("Processed/sec", snapshot.processedPerSecond())
                        .add("Coalesced/sec", snapshot.coalescedPerSecond())
                        .add("Dropped/superseded", snapshot.droppedOrSupersededUpdates())
                        .build(),
                DebugSection.builder("NETWORKING")
                        .add("Batches", snapshot.networkBatches())
                        .add("Entries", snapshot.networkEntries())
                        .add("Estimated sent", kibibytes(snapshot.estimatedBytesSent()))
                        .add("Interest filtered", snapshot.interestFilteredUpdates())
                        .build(),
                DebugSection.builder("CACHE / WORKERS")
                        .add("Cache hit rate", String.format(Locale.ROOT, "%.1f%%", snapshot.cacheHitRate() * 100.0D))
                        .add("Cache entries", snapshot.cacheEntries())
                        .add("Worker threads", snapshot.workerThreads())
                        .add("Worker queue", snapshot.workerQueueLength())
                        .add("Async complete/rejected", snapshot.asyncTasksCompleted() + " / " + snapshot.asyncTasksRejected())
                        .build()
        );
    }

    private static String milliseconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f ms", nanos / 1_000_000.0D);
    }

    private static String kibibytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f KiB total", bytes / 1024.0D);
    }
}
