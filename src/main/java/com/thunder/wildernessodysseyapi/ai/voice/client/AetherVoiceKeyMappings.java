package com.thunder.wildernessodysseyapi.ai.voice.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Controls-screen bindings for optional local voice input and diagnostics. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AetherVoiceKeyMappings {
    private static final String CATEGORY = "key.categories.wildernessodysseyapi";

    public static final KeyMapping PUSH_TO_TALK = new KeyMapping(
            "key.wildernessodysseyapi.aether_push_to_talk",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );
    public static final KeyMapping VOICE_STATUS = new KeyMapping(
            "key.wildernessodysseyapi.aether_voice_status",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    private AetherVoiceKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PUSH_TO_TALK);
        event.register(VOICE_STATUS);
    }
}
