package com.thunder.wildernessodysseyapi.performance.tickengine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies headroom reservation and pressure-scaled optional capacity. */
class TickBudgetManagerTest {

    @Test
    void reservesSoftTargetHeadroomAndScalesRemainingCapacity() {
        TickBudgetManager manager = new TickBudgetManager();
        manager.configure(new TickBudgetManager.Settings(50.0D, 45.0D, 1.0D, 0.7D, 0.35D, 0.1D, 0.0D));
        long ordinaryWork = TimeUnit.MILLISECONDS.toNanos(20L);

        TickBudget relaxed = manager.begin(0L, ordinaryWork, TickPressure.RELAXED, 1.0D);
        assertEquals(25.0D, relaxed.allowedWorkNanos() / 1_000_000.0D, 0.0001D);
        TickBudget busy = manager.begin(0L, ordinaryWork, TickPressure.BUSY, 1.0D);
        assertEquals(17.5D, busy.allowedWorkNanos() / 1_000_000.0D, 0.0001D);
        TickBudget overloaded = manager.begin(0L, ordinaryWork, TickPressure.OVERLOADED, 1.0D);
        assertEquals(0L, overloaded.allowedWorkNanos());
    }

    @Test
    void exhaustedSoftBudgetNeverBorrowsFromTargetHeadroom() {
        TickBudgetManager manager = new TickBudgetManager();
        manager.configure(TickBudgetManager.Settings.defaults());

        TickBudget budget = manager.begin(
                0L,
                TimeUnit.MILLISECONDS.toNanos(48L),
                TickPressure.RELAXED,
                1.0D
        );

        assertEquals(0L, budget.allowedWorkNanos());
    }
}
