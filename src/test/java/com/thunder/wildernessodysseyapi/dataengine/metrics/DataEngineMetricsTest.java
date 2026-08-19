package com.thunder.wildernessodysseyapi.dataengine.metrics;

import com.thunder.wildernessodysseyapi.dataengine.queue.DataUpdateQueue;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

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
}
