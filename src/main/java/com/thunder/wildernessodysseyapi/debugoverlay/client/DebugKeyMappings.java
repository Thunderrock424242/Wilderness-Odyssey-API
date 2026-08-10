package com.thunder.wildernessodysseyapi.debugoverlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Configurable keys used to navigate pages while the F3 overlay is visible. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class DebugKeyMappings {
    private static final String CATEGORY = "key.categories.wildernessodysseyapi";

    public static final KeyMapping PREVIOUS_PAGE = new KeyMapping(
            "key.wildernessodysseyapi.debug_previous_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            CATEGORY
    );
    public static final KeyMapping NEXT_PAGE = new KeyMapping(
            "key.wildernessodysseyapi.debug_next_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            CATEGORY
    );
    public static final KeyMapping SCROLL_UP = new KeyMapping(
            "key.wildernessodysseyapi.debug_scroll_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            CATEGORY
    );
    public static final KeyMapping SCROLL_DOWN = new KeyMapping(
            "key.wildernessodysseyapi.debug_scroll_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            CATEGORY
    );

    private DebugKeyMappings() {
    }

    /** Resolves GLFW key and scan codes to one of the configured page actions. */
    public static NavigationAction navigationActionFor(int key, int scanCode) {
        if (PREVIOUS_PAGE.matches(key, scanCode)) {
            return NavigationAction.PREVIOUS;
        }
        if (NEXT_PAGE.matches(key, scanCode)) {
            return NavigationAction.NEXT;
        }
        if (SCROLL_UP.matches(key, scanCode)) {
            return NavigationAction.SCROLL_UP;
        }
        if (SCROLL_DOWN.matches(key, scanCode)) {
            return NavigationAction.SCROLL_DOWN;
        }
        return NavigationAction.NONE;
    }

    /** Adds all four debug navigation keys to Minecraft's normal Controls screen. */
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PREVIOUS_PAGE);
        event.register(NEXT_PAGE);
        event.register(SCROLL_UP);
        event.register(SCROLL_DOWN);
    }

    /** Page movement recognized by the debug-overlay input handler. */
    public enum NavigationAction {
        PREVIOUS,
        NEXT,
        SCROLL_UP,
        SCROLL_DOWN,
        NONE
    }
}
