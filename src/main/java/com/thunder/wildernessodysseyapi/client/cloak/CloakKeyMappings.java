package com.thunder.wildernessodysseyapi.client.cloak;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CloakKeyMappings {
    private static final String CATEGORY = "key.categories.wildernessodysseyapi";

    public static final KeyMapping CLOAK_ALT = new KeyMapping(
            "key.wildernessodysseyapi.cloak_alt",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
    );

    private CloakKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CLOAK_ALT);
    }
}
