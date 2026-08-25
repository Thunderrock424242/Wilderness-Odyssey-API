package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that A.E.T.H.E.R cannot cross the single-player chat boundary. */
class AIChatAccessPolicyTest {

    @Test
    void allowsPrivateIntegratedWorld() {
        assertTrue(AIChatAccessPolicy.isAvailable(true, false));
    }

    @Test
    void rejectsWorldAsSoonAsItIsPublishedToLan() {
        assertFalse(AIChatAccessPolicy.isAvailable(true, true));
    }

    @Test
    void rejectsDedicatedAndNonSingleplayerServers() {
        assertFalse(AIChatAccessPolicy.isAvailable(false, true));
        assertFalse(AIChatAccessPolicy.isAvailable(false, false));
    }
}
