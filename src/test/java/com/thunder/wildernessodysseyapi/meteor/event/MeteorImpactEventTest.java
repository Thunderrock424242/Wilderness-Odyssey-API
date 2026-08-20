package com.thunder.wildernessodysseyapi.meteor.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the server-owned meteor queue cannot accept unbounded command bursts. */
class MeteorImpactEventTest {

    @Test
    void capsIndividualRequestsAndTotalQueuedWork() {
        assertEquals(0, MeteorImpactEvent.acceptedMeteorCount(-5, 0));
        assertEquals(20, MeteorImpactEvent.acceptedMeteorCount(100, 0));
        assertEquals(4, MeteorImpactEvent.acceptedMeteorCount(20, 60));
        assertEquals(0, MeteorImpactEvent.acceptedMeteorCount(1, 64));
        assertEquals(0, MeteorImpactEvent.acceptedMeteorCount(1, 80));
    }
}
