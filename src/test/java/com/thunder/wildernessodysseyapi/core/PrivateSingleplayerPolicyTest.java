package com.thunder.wildernessodysseyapi.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateSingleplayerPolicyTest {
    @Test
    void permitsOnlyAnUnpublishedIntegratedWorld() {
        assertTrue(PrivateSingleplayerPolicy.permits(true, false));
        assertFalse(PrivateSingleplayerPolicy.permits(true, true));
        assertFalse(PrivateSingleplayerPolicy.permits(false, false));
        assertFalse(PrivateSingleplayerPolicy.permits(false, true));
    }
}
