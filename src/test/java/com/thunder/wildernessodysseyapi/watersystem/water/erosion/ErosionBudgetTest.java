package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErosionBudgetTest {
    @Test void limitsCombinedChangesAndChunkCooldownAcrossMinuteBoundaries() {
        ErosionBudget budget = new ErosionBudget();
        assertTrue(budget.allows(1199, 1, 2));
        budget.record(1199, 1);
        assertFalse(budget.allows(1200, 1, 2));
        assertTrue(budget.allows(1200, 2, 2));
        budget.record(1200, 2);
        assertFalse(budget.allows(1201, 3, 2));
        assertFalse(budget.allows(2398, 1, 2));
        assertTrue(budget.allows(2399, 1, 2));
    }

    @Test void dryRunDoesNotSpendBudgetAndZeroBudgetDisablesMutation() {
        ErosionBudget budget = new ErosionBudget();
        for (int i = 0; i < 20; i++) assertTrue(budget.allows(100, 1, 1));
        assertFalse(budget.allows(100, 1, 0));
        budget.record(100, 1);
        assertFalse(budget.allows(101, 2, 1));
    }
}
