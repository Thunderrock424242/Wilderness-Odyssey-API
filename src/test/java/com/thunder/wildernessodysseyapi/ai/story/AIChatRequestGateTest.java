package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Pending turns stay bounded without allowing the same player to race conversation state. */
class AIChatRequestGateTest {
    @Test
    void boundsDistinctPlayersAndOverlappingTurns() {
        AIChatRequestGate gate = new AIChatRequestGate(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        assertTrue(gate.acquire(first));
        assertFalse(gate.acquire(first));
        assertTrue(gate.acquire(second));
        assertFalse(gate.acquire(third));
        gate.release(first);
        assertTrue(gate.acquire(third));
    }
}
