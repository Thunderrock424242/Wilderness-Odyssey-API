package com.thunder.wildernessodysseyapi.debugoverlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Configurable keys used as F3 chords for page navigation. */
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

    private DebugKeyMappings() {
    }

    /** Adds both page keys to Minecraft's normal Controls screen. */
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PREVIOUS_PAGE);
        event.register(NEXT_PAGE);
    }
}
