package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Multiplayer chat requires an explicit server-owner opt-in. */
class AIChatAccessPolicyTest {
    @Test
    void permitsPrivateSingleplayerByDefault() {
        assertTrue(AIChatAccessPolicy.isAvailable(true, false));
        assertFalse(AIChatAccessPolicy.isAvailable(null));
    }

    @Test
    void disablesLanAndDedicatedChatByDefault() {
        assertFalse(AIChatAccessPolicy.isAvailable(true, true));
        assertFalse(AIChatAccessPolicy.isAvailable(false, false));
        assertFalse(AIChatAccessPolicy.isAvailable(false, true));
    }

    @Test
    void permitsMultiplayerOnlyWhenExplicitlyEnabled() {
        assertTrue(AIChatAccessPolicy.isAvailable(true, false, true));
        assertTrue(AIChatAccessPolicy.isAvailable(true, true, true));
        assertTrue(AIChatAccessPolicy.isAvailable(false, false, true));
        assertTrue(AIChatAccessPolicy.isAvailable(false, true, true));
        assertFalse(AIChatAccessPolicy.isAvailable(null, true));
    }

    @Test
    void openingPrivateWorldToLanRevokesDefaultAccess() {
        assertTrue(AIChatAccessPolicy.isAvailable(true, false, false));
        assertFalse(AIChatAccessPolicy.isAvailable(true, true, false));
    }
}
