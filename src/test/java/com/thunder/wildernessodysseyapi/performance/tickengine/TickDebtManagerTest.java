package com.thunder.wildernessodysseyapi.performance.tickengine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies collapsed, bounded-individual, and explicitly discarded missed work. */
class TickDebtManagerTest {

    @Test
    void collapsesElapsedTimeIntoOneSimulationCall() {
        TickDebtManager manager = new TickDebtManager();
        AtomicInteger calls = new AtomicInteger();
        AtomicLong elapsed = new AtomicLong();
        TickDebtAware simulation = simulation(TickDebtAware.MissedTickPolicy.COLLAPSE, calls, elapsed);

        TickDebtManager.Result result = manager.process(simulation, 1000L, 1200L);

        assertEquals(1, calls.get());
        assertEquals(200L, elapsed.get());
        assertEquals(0L, result.remainingTicks());
    }

    @Test
    void boundsRequiredIndividualCatchUpAndRetainsRemainingDebt() {
        TickDebtManager manager = new TickDebtManager();
        manager.configure(true, 8);
        AtomicInteger calls = new AtomicInteger();
        TickDebtAware simulation = simulation(TickDebtAware.MissedTickPolicy.INDIVIDUAL, calls, new AtomicLong());

        TickDebtManager.Result result = manager.process(simulation, 1000L, 1200L);

        assertEquals(8, calls.get());
        assertEquals(1008L, result.accountedThroughTick());
        assertEquals(192L, result.remainingTicks());
    }

    @Test
    void discardPolicyRequiresExplicitOptInAndPerformsNoCatchUp() {
        TickDebtManager manager = new TickDebtManager();
        AtomicInteger calls = new AtomicInteger();
        TickDebtAware simulation = simulation(TickDebtAware.MissedTickPolicy.DISCARD, calls, new AtomicLong());

        TickDebtManager.Result result = manager.process(simulation, 1000L, 1200L);

        assertEquals(0, calls.get());
        assertEquals(1200L, result.accountedThroughTick());
    }

    private static TickDebtAware simulation(
            TickDebtAware.MissedTickPolicy policy,
            AtomicInteger calls,
            AtomicLong elapsed
    ) {
        return new TickDebtAware() {
            @Override
            public MissedTickPolicy missedTickPolicy() {
                return policy;
            }

            @Override
            public void advanceSimulation(long elapsedTicks) {
                calls.incrementAndGet();
                elapsed.addAndGet(elapsedTicks);
            }
        };
    }
}
