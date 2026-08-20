package com.thunder.wildernessodysseyapi.worldgen.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that delayed cryo discovery uses a bounded retry cadence. */
class PlayerSpawnHandlerTest {

    @Test
    void retriesOnlyOncePerSecond() {
        assertTrue(PlayerSpawnHandler.shouldRetryAssignment(true, 0));
        assertFalse(PlayerSpawnHandler.shouldRetryAssignment(true, 1));
        assertFalse(PlayerSpawnHandler.shouldRetryAssignment(true, 19));
        assertTrue(PlayerSpawnHandler.shouldRetryAssignment(true, 20));
        assertFalse(PlayerSpawnHandler.shouldRetryAssignment(true, 39));
        assertTrue(PlayerSpawnHandler.shouldRetryAssignment(true, 40));
    }

    @Test
    void assignedPlayersNeverEnterTheRetryPath() {
        assertFalse(PlayerSpawnHandler.shouldRetryAssignment(false, 0));
        assertFalse(PlayerSpawnHandler.shouldRetryAssignment(false, 20));
        assertFalse(PlayerSpawnHandler.shouldRetryAssignment(false, 40));
    }
}
