package com.thunder.wildernessodysseyapi.dataengine.metrics;

import com.thunder.wildernessodysseyapi.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.dataengine.queue.DataUpdateQueue;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataEngineMetricsTest {
    private static final ResourceLocation SYSTEM = ResourceLocation.fromNamespaceAndPath("test", "old_system");

    @Test
    void newServerLifecycleClearsTotalsGaugesAndOldSystemRegistrations() {
        DataEngineMetrics metrics = new DataEngineMetrics(true);
        metrics.registerSystem(SYSTEM);
        metrics.recordSubmission(SYSTEM, DataUpdateQueue.SubmissionResult.ACCEPTED);
        metrics.updateGauges(4, 3, 9, 2, 1, true);

        metrics.beginServerLifecycle(true);

        DataEngineMetricsSnapshot snapshot = metrics.snapshot();
        assertTrue(snapshot.enabled());
        assertEquals(0, snapshot.updatesSubmitted());
        assertEquals(0, snapshot.dirtyEntries());
        assertEquals(0, snapshot.queuedWork());
        assertEquals(0, snapshot.queuePeak());
        assertFalse(snapshot.backpressureActive());
        assertTrue(snapshot.systems().isEmpty());
    }

    @Test
    void attributesRealNetworkEntriesToTheirOwningSystem() {
        DataEngineMetrics metrics = new DataEngineMetrics(true);
        DataDelta first = new DataDelta(SYSTEM, 1L, 1L, UpdatePriority.NORMAL, new byte[]{1, 2});
        DataDelta second = new DataDelta(SYSTEM, 2L, 1L, UpdatePriority.NORMAL, new byte[]{3});

        metrics.recordNetworkBatch(List.of(first, second), 123L);

        DataEngineMetricsSnapshot snapshot = metrics.snapshot();
        DataSystemMetricsSnapshot system = snapshot.systems().get(SYSTEM);
        assertEquals(1L, snapshot.networkBatches());
        assertEquals(2L, snapshot.networkEntries());
        assertEquals(123L, snapshot.estimatedBytesSent());
        assertEquals(1L, system.networkBatches());
        assertEquals(2L, system.networkEntries());
        assertEquals(
                first.approximateEncodedBytes() + second.approximateEncodedBytes(),
                system.estimatedNetworkBytes()
        );
    }
}
