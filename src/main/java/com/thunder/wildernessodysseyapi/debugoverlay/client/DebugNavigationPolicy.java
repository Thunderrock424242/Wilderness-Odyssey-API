package com.thunder.wildernessodysseyapi.debugoverlay.client;

import org.lwjgl.glfw.GLFW;

/** Pure eligibility checks shared by debug-page keyboard handling and tests. */
final class DebugNavigationPolicy {
    private DebugNavigationPolicy() {
    }

    /**
     * Accepts only a fresh non-Esc press during an active gameplay F3 session.
     * Esc is rejected explicitly because configurable page mappings must never
     * acquire a side effect when vanilla opens or closes a screen with that key.
     */
    static boolean canHandle(
            int action,
            int key,
            boolean enabled,
            boolean noScreen,
            boolean sessionReady,
            boolean debugVisible
    ) {
        return action == GLFW.GLFW_PRESS
                && key != GLFW.GLFW_KEY_ESCAPE
                && enabled
                && noScreen
                && sessionReady
                && debugVisible;
    }
}
