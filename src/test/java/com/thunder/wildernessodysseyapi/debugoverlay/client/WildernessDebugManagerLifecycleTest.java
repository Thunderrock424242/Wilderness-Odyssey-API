package com.thunder.wildernessodysseyapi.debugoverlay.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildernessDebugManagerLifecycleTest {
    private final WildernessDebugManager manager = WildernessDebugManager.get();

    @AfterEach
    void resetSingleton() {
        manager.resetForSession();
    }

    @Test
    void screenTransitionClearsVisibilityAndScrollWithoutChangingVanillaState() {
        manager.synchronizeVisibility(true, true);
        manager.scrollDown();

        manager.onScreenOpening(null, true);

        assertFalse(manager.wasVisible());
        assertEquals(0, manager.scrollOffset());
    }

    @Test
    void reopeningStartsASeparateVisibleSession() {
        manager.synchronizeVisibility(true, true);
        manager.scrollDown();
        manager.synchronizeVisibility(false, true);
        manager.synchronizeVisibility(true, true);

        assertTrue(manager.wasVisible());
        assertEquals(0, manager.scrollOffset());
    }

    @Test
    void sessionResetClearsSelectedPageAndTransientState() {
        manager.synchronizeVisibility(true, true);
        manager.nextPage();
        manager.scrollDown();

        manager.resetForSession();

        assertEquals(0, manager.selectedPageIndex());
        assertEquals(0, manager.scrollOffset());
        assertFalse(manager.wasVisible());
    }
}
