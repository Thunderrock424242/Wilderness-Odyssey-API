package com.thunder.wildernessodysseyapi.watersystem.water.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies amount and outcome invariants at the public mutation boundary. */
class WaterInteractionResultTest {

    @Test
    void classifiesCompletePartialAndRejectedTransfers() {
        WaterInteractionResult complete = WaterInteractionResult.transferred(4096L, 4096L, false);
        WaterInteractionResult partial = WaterInteractionResult.transferred(4096L, 1024L, true);
        WaterInteractionResult rejected = WaterInteractionResult.transferred(4096L, 0L, true);

        assertEquals(WaterInteractionResult.Outcome.SUCCESS, complete.outcome());
        assertTrue(complete.successful());
        assertEquals(WaterInteractionResult.Outcome.PARTIAL, partial.outcome());
        assertTrue(partial.successful());
        assertTrue(partial.simulated());
        assertEquals(WaterInteractionResult.Outcome.REJECTED, rejected.outcome());
        assertFalse(rejected.successful());
    }

    @Test
    void rejectsAmountsOutsideTheRequestedRange() {
        assertThrows(IllegalArgumentException.class, () -> new WaterInteractionResult(
                WaterInteractionResult.Outcome.SUCCESS,
                10L,
                11L,
                false
        ));
        assertThrows(IllegalArgumentException.class, () -> new WaterInteractionResult(
                WaterInteractionResult.Outcome.REJECTED,
                -1L,
                0L,
                false
        ));
    }
}
