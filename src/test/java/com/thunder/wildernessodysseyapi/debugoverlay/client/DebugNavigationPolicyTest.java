package com.thunder.wildernessodysseyapi.debugoverlay.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugNavigationPolicyTest {

    @Test
    void acceptsArrowPressOnlyDuringActiveGameplayF3() {
        assertTrue(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_PRESS, GLFW.GLFW_KEY_LEFT, true, true, true, true));
    }

    @Test
    void rejectsEscapeEvenIfAConfigurableMappingUsesIt() {
        assertFalse(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_PRESS, GLFW.GLFW_KEY_ESCAPE, true, true, true, true));
    }

    @Test
    void rejectsInputWhileScreenOrSessionOwnsTheTransition() {
        assertFalse(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_PRESS, GLFW.GLFW_KEY_LEFT, true, false, true, true));
        assertFalse(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_PRESS, GLFW.GLFW_KEY_LEFT, true, true, false, true));
        assertFalse(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_PRESS, GLFW.GLFW_KEY_LEFT, true, true, true, false));
    }

    @Test
    void rejectsReleaseAndRepeatActions() {
        assertFalse(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_RELEASE, GLFW.GLFW_KEY_LEFT, true, true, true, true));
        assertFalse(DebugNavigationPolicy.canHandle(
                GLFW.GLFW_REPEAT, GLFW.GLFW_KEY_LEFT, true, true, true, true));
    }
}
