package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestRequestGateTest {
    @Test void duplicateIdsAndRateLimitsAreBoundedAndLogoutClearsTheWindow() {
        var gate = new QuestRequestGate(); var settings = new QuestConfig.Settings(true, 2, 3, 1, 2, 8);
        UUID player = UUID.randomUUID(); UUID request = UUID.randomUUID();
        assertTrue(gate.admit(player, request, 0, false, settings));
        assertFalse(gate.admit(player, request, 0, false, settings));
        assertTrue(gate.admit(player, UUID.randomUUID(), 0, false, settings));
        assertFalse(gate.admit(player, UUID.randomUUID(), 0, false, settings));
        assertTrue(gate.admit(player, UUID.randomUUID(), 20, false, settings));
        gate.clear(player); assertTrue(gate.admit(player, request, 20, false, settings));
    }
    @Test void completeViewsHaveTheirOwnSlowerBudget() {
        var gate = new QuestRequestGate(); var settings = new QuestConfig.Settings(true, 2, 3, 10, 20, 8);
        UUID player = UUID.randomUUID();
        assertTrue(gate.admit(player, UUID.randomUUID(), 0, true, settings));
        assertFalse(gate.admit(player, UUID.randomUUID(), 1, true, settings));
        assertTrue(gate.admit(player, UUID.randomUUID(), 40, true, settings));
    }
}
