package com.thunder.wildernessodysseyapi.performance.coordinator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoordinatorGovernorTest {
    @Test
    void entryWaitsForReadinessAndHealthyTicks() {
        var governor = governor();
        healthy(governor, 300);
        assertEquals(CoordinatorGovernor.State.ENTRY, governor.state());
        assertEquals(0, governor.budgetNanos());
        governor.ready();
        healthy(governor, 100);
        assertTrue(governor.budgetNanos() > 0);
        assertTrue(governor.budgetNanos() <= 2_000_000);
    }

    @Test
    void overloadBacksOffImmediatelyAndRequiresStableRecovery() {
        var governor = running();
        governor.observe(200, 49, 0.1, 0, 0);
        assertEquals(0, governor.budgetNanos());
        assertEquals(CoordinatorGovernor.State.OVERLOADED, governor.state());
        governor.observe(201, 44, 0.1, 0, 0);
        assertEquals(0, governor.budgetNanos());
        for (int tick = 202; tick < 302; tick++) governor.observe(tick, 20, 0.1, 0, 0);
        assertTrue(governor.budgetNanos() > 0);
    }

    @Test
    void cpuGcAndQueueSaturationSuppressOptionalWork() {
        for (double[] pressure : new double[][] {{0.95, 0, 0}, {0.1, 0.2, 0}, {0.1, 0, 0.9}}) {
            var governor = running();
            governor.observe(200, 20, pressure[0], pressure[1], pressure[2]);
            assertEquals(0, governor.budgetNanos());
        }
    }

    @Test
    void recentChunkWaitYieldsWithoutClaimingGenerationThroughput() {
        var governor = running();
        governor.chunkWait(200);
        governor.observe(201, 20, -1, -1, 0);
        assertEquals(CoordinatorGovernor.State.CHUNK_PRESSURE, governor.state());
        assertEquals(0, governor.budgetNanos());
    }

    @Test
    void repeatedOverrunsHalveThenPauseOnlyTheOffendingModule() {
        var governor = running();
        long original = governor.moduleBudget(CoordinatorGovernor.Module.SAVE);
        governor.recordCost(CoordinatorGovernor.Module.SAVE, 3_000_000, 1_000_000, 200);
        assertTrue(governor.moduleBudget(CoordinatorGovernor.Module.SAVE) < original);
        governor.recordCost(CoordinatorGovernor.Module.SAVE, 3_000_000, 1_000_000, 201);
        governor.recordCost(CoordinatorGovernor.Module.SAVE, 3_000_000, 1_000_000, 202);
        assertEquals(0, governor.moduleBudget(CoordinatorGovernor.Module.SAVE));
        assertTrue(governor.moduleBudget(CoordinatorGovernor.Module.BACKGROUND) > 0);
        for (int tick = 203; tick < 404; tick++) governor.observe(tick, 20, 0.1, 0, 0);
        assertTrue(governor.moduleBudget(CoordinatorGovernor.Module.SAVE) > 0);
    }

    @Test
    void stoppingNeverRestoresOptionalCapacity() {
        var governor = running();
        governor.stop();
        healthy(governor, 500);
        assertEquals(CoordinatorGovernor.State.STOPPING, governor.state());
        assertEquals(0, governor.budgetNanos());
    }

    private static CoordinatorGovernor governor() { return new CoordinatorGovernor(2_000_000, 0.1, 100, 200); }
    private static CoordinatorGovernor running() {
        var governor = governor();
        governor.ready();
        healthy(governor, 120);
        return governor;
    }
    private static void healthy(CoordinatorGovernor governor, int count) {
        for (int tick = 1; tick <= count; tick++) governor.observe(tick, 20, 0.1, 0, 0);
    }
}
