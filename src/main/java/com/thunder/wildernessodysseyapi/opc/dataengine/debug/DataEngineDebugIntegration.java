package com.thunder.wildernessodysseyapi.opc.dataengine.debug;

import com.thunder.wildernessodysseyapi.opc.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.opc.async.AsyncTaskStats;
import com.thunder.wildernessodysseyapi.opc.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.opc.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.opc.dataengine.DataSystemRegistration;
import com.thunder.wildernessodysseyapi.opc.dataengine.metrics.DataEngineMetricsSnapshot;
import com.thunder.wildernessodysseyapi.opc.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.opc.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.opc.dataengine.scheduler.UpdateFrequency;

/**
 * Low-risk proof integration with the existing Wilderness debug HUD.
 *
 * <p>scheduler -> dirty mark -> coalesced work -> explicit player interest ->
 * compact delta -> bounded network batch. No metric packet is created when no
 * operator is actively subscribed to the page.</p>
 */
public final class DataEngineDebugIntegration {
    private static final long ALL_DEBUG_FIELDS = 1L;

    private DataEngineDebugIntegration() {
    }

    /** Registers the internal metrics producer for the current server lifecycle. */
    public static void register(DataEngine engine) {
        engine.registerSystem(DataSystemRegistration.builder(DataEngineIds.DEBUG_METRICS)
                .frequency(UpdateFrequency.NORMAL)
                .intervalTicks(() -> 20)
                .priority(UpdatePriority.LOW)
                .onScheduledUpdate(server -> {
                    if (!engine.interest().hasFeatureSubscribers(DataEngineIds.DEBUG_METRICS)) return;
                    engine.markDirty(
                        DataEngineIds.DEBUG_METRICS,
                        0L,
                        "periodic operator debug metrics",
                        UpdatePriority.LOW
                    );
                })
                .onDirtyUpdate((server, dirty) -> {
                    if (!engine.interest().hasFeatureSubscribers(DataEngineIds.DEBUG_METRICS)) return;
                    DataEngineMetricsSnapshot metrics = engine.metricsSnapshot();
                    AsyncTaskStats workers = AsyncTaskManager.snapshot();
                    DataEngineDebugSnapshot snapshot = new DataEngineDebugSnapshot(
                            server.getTickCount(),
                            engine.config().tickBudgetNanos(),
                            metrics.lastMainThreadProcessingNanos(),
                            metrics.dirtyEntries(),
                            metrics.queuedWork(),
                            metrics.processedPerSecond(),
                            metrics.coalescedPerSecond(),
                            metrics.networkBatches(),
                            metrics.networkEntries(),
                            metrics.estimatedBytesSent(),
                            metrics.cacheHits(),
                            metrics.cacheMisses(),
                            metrics.cacheEntries(),
                            metrics.asyncTasksSubmitted(),
                            metrics.asyncTasksCompleted(),
                            metrics.asyncTasksRejected(),
                            workers.configuredThreads(),
                            workers.queuedWorkerTasks(),
                            metrics.interestFilteredUpdates(),
                            metrics.droppedOrSupersededBackgroundUpdates(),
                            metrics.backpressureActive()
                    );
                    engine.sendDeltaToFeature(
                            DataEngineIds.DEBUG_METRICS,
                            new DataDelta(
                                    DataEngineIds.DEBUG_METRICS,
                                    0L,
                                    ALL_DEBUG_FIELDS,
                                    UpdatePriority.LOW,
                                    DataEngineDebugSnapshotCodec.encode(snapshot)
                            )
                    );
                })
                .build());
    }
}
