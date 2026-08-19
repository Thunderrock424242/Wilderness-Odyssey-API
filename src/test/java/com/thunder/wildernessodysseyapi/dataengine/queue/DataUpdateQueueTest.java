package com.thunder.wildernessodysseyapi.dataengine.queue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataUpdateQueueTest {
    private static final ResourceLocation SYSTEM = ResourceLocation.fromNamespaceAndPath("test", "queue");

    @Test
    void coalescesRepeatedFinalStateToLatestAction() {
        DataUpdateQueue queue = new DataUpdateQueue(16);
        AtomicInteger value = new AtomicInteger();

        queue.submit(QueuedUpdate.dirty(SYSTEM, 1L, UpdatePriority.NORMAL, 1L, () -> value.set(1)));
        assertEquals(DataUpdateQueue.SubmissionResult.COALESCED,
                queue.submit(QueuedUpdate.dirty(SYSTEM, 1L, UpdatePriority.NORMAL, 2L, () -> value.set(2))));
        assertEquals(DataUpdateQueue.SubmissionResult.COALESCED,
                queue.submit(QueuedUpdate.dirty(SYSTEM, 1L, UpdatePriority.NORMAL, 3L, () -> value.set(3))));

        assertEquals(1, queue.size());
        queue.processAvailable(1_000_000L, QueuedUpdate::run);
        assertEquals(3, value.get());
    }

    @Test
    void criticalWorkExecutesBeforeBackgroundWork() {
        DataUpdateQueue queue = new DataUpdateQueue(16);
        List<String> order = new ArrayList<>();
        queue.submit(QueuedUpdate.event(SYSTEM, 1L, UpdatePriority.BACKGROUND, 0L, () -> order.add("background")));
        queue.submit(QueuedUpdate.event(SYSTEM, 2L, UpdatePriority.CRITICAL, 0L, () -> order.add("critical")));

        queue.processAvailable(1_000_000L, update -> update.run());

        assertEquals(List.of("critical", "background"), order);
    }

    @Test
    void budgetStopsNonCriticalDrain() {
        AtomicLong clock = new AtomicLong();
        DataUpdateQueue queue = new DataUpdateQueue(16, clock::get);
        AtomicInteger processed = new AtomicInteger();
        for (int key = 0; key < 3; key++) {
            queue.submit(QueuedUpdate.event(SYSTEM, key, UpdatePriority.NORMAL, 0L, () -> {
                processed.incrementAndGet();
                clock.addAndGet(2_000_000L);
            }));
        }

        queue.processAvailable(2_000_000L, QueuedUpdate::run);

        assertEquals(1, processed.get());
        assertEquals(2, queue.size());
    }

    @Test
    void backpressureEvictsSupersedableBackgroundForCriticalEvent() {
        DataUpdateQueue queue = new DataUpdateQueue(2);
        queue.submit(QueuedUpdate.dirty(SYSTEM, 1L, UpdatePriority.BACKGROUND, 0L, () -> { }));
        queue.submit(QueuedUpdate.dirty(SYSTEM, 2L, UpdatePriority.LOW, 0L, () -> { }));

        DataUpdateQueue.SubmissionResult result = queue.submit(
                QueuedUpdate.event(SYSTEM, 3L, UpdatePriority.CRITICAL, 0L, () -> { })
        );

        assertEquals(DataUpdateQueue.SubmissionResult.ACCEPTED_WITH_EVICTION, result);
        assertEquals(UpdatePriority.CRITICAL, queue.pollCritical().priority());
        assertTrue(queue.size() <= queue.maximumSize());
    }
}
