package com.thunder.wildernessodysseyapi.dataengine.scheduler;

import com.thunder.wildernessodysseyapi.dataengine.DataSystemRegistration;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataUpdateSchedulerTest {
    @Test
    void emitsAtCentralIntervalWithoutCatchUpStorm() {
        DataUpdateScheduler scheduler = new DataUpdateScheduler();
        AtomicInteger interval = new AtomicInteger(5);
        DataSystemRegistration registration = DataSystemRegistration.builder(
                        ResourceLocation.fromNamespaceAndPath("test", "scheduled"))
                .frequency(UpdateFrequency.NORMAL)
                .intervalTicks(interval::get)
                .onScheduledUpdate(server -> { })
                .build();
        scheduler.register(registration, 10L);

        AtomicInteger emitted = new AtomicInteger();
        assertEquals(0, scheduler.collectDue(14L, ignored -> emitted.incrementAndGet()));
        assertEquals(1, scheduler.collectDue(15L, ignored -> emitted.incrementAndGet()));
        assertEquals(1, scheduler.collectDue(100L, ignored -> emitted.incrementAndGet()));
        assertEquals(2, emitted.get());
    }
}
