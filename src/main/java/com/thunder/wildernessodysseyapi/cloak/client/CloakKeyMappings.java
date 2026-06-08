package com.thunder.wildernessodysseyapi.cloak.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CloakKeyMappings {
    private static final String CATEGORY = "key.categories.wildernessodysseyapi";

    public static final KeyMapping HOLD_BREATH = new KeyMapping(
            "key.wildernessodysseyapi.hold_breath",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
    );

    public static final KeyMapping CLOAK_TOGGLE = new KeyMapping(
            "key.wildernessodysseyapi.cloak_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    private CloakKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(HOLD_BREATH);
        event.register(CLOAK_TOGGLE);
    }
}
