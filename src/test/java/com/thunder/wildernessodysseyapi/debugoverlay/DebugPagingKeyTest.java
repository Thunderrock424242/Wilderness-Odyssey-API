package com.thunder.wildernessodysseyapi.debugoverlay;

import com.thunder.wildernessodysseyapi.debugoverlay.client.DebugKeyMappings;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugPagingKeyTest {
    @Test
    void defaultArrowKeysResolveWithoutClaimingUnrelatedKeys() {
        assertEquals(DebugKeyMappings.NavigationAction.PREVIOUS,
                DebugKeyMappings.navigationActionFor(GLFW.GLFW_KEY_LEFT, 0));
        assertEquals(DebugKeyMappings.NavigationAction.NEXT,
                DebugKeyMappings.navigationActionFor(GLFW.GLFW_KEY_RIGHT, 0));
        assertEquals(DebugKeyMappings.NavigationAction.SCROLL_UP,
                DebugKeyMappings.navigationActionFor(GLFW.GLFW_KEY_UP, 0));
        assertEquals(DebugKeyMappings.NavigationAction.SCROLL_DOWN,
                DebugKeyMappings.navigationActionFor(GLFW.GLFW_KEY_DOWN, 0));
        assertEquals(DebugKeyMappings.NavigationAction.NONE,
                DebugKeyMappings.navigationActionFor(GLFW.GLFW_KEY_A, 0));
        assertEquals(DebugKeyMappings.NavigationAction.NONE,
                DebugKeyMappings.navigationActionFor(GLFW.GLFW_KEY_F4, 0));
    }
}
