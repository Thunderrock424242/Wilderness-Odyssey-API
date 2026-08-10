package com.thunder.wildernessodysseyapi.debugoverlay.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugViewportTest {
    @Test
    void contentThatFitsAlwaysStartsAtTheTop() {
        DebugViewport viewport = DebugViewport.calculate(12, 15, 20);

        assertEquals(0, viewport.offset());
        assertEquals(1, viewport.firstVisibleLine());
        assertEquals(15, viewport.lastVisibleLine());
        assertFalse(viewport.scrollable());
    }

    @Test
    void overflowingContentUsesTheRequestedRowOffset() {
        DebugViewport viewport = DebugViewport.calculate(3, 50, 20);

        assertEquals(3, viewport.offset());
        assertEquals(4, viewport.firstVisibleLine());
        assertEquals(23, viewport.lastVisibleLine());
        assertTrue(viewport.scrollable());
    }

    @Test
    void offsetsClampAtBothEndsWhenContentOrWindowChanges() {
        assertEquals(0, DebugViewport.calculate(-4, 50, 20).offset());
        assertEquals(30, DebugViewport.calculate(99, 50, 20).offset());
        assertEquals(0, DebugViewport.calculate(4, 0, 0).offset());
    }
}
