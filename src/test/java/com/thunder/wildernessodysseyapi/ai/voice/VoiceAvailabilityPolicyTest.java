package com.thunder.wildernessodysseyapi.ai.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAvailabilityPolicyTest {
    @Test
    void requiresExplicitOptInInsideAPrivateSingleplayerWorld() {
        assertTrue(VoiceAvailabilityPolicy.permits(true, true));
        assertFalse(VoiceAvailabilityPolicy.permits(false, true));
        assertFalse(VoiceAvailabilityPolicy.permits(true, false));
        assertFalse(VoiceAvailabilityPolicy.permits(false, false));
    }
}
