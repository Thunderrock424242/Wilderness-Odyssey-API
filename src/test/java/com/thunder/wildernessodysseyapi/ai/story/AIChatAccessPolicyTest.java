package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** All supported hosting modes share the same server-side network boundary. */
class AIChatAccessPolicyTest {
    @Test
    void supportsIntegratedLanAndDedicatedHosting() {
        assertTrue(AIChatAccessPolicy.isAvailable(true, false));
        assertTrue(AIChatAccessPolicy.isAvailable(true, true));
        assertTrue(AIChatAccessPolicy.isAvailable(false, false));
        assertTrue(AIChatAccessPolicy.isAvailable(false, true));
        assertFalse(AIChatAccessPolicy.isAvailable(null));
    }
}
