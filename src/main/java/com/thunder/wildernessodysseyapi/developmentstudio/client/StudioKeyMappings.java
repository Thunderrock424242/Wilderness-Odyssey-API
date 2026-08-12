package com.thunder.wildernessodysseyapi.developmentstudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Configurable key used to request the server-authorized Studio screen. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class StudioKeyMappings {
    private static final String CATEGORY = "key.categories.wildernessodysseyapi";

    public static final KeyMapping OPEN_STUDIO = new KeyMapping(
            "key.wildernessodysseyapi.open_studio",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
    );

    private StudioKeyMappings() {
    }

    /** Adds F8 to Minecraft's normal configurable Controls screen. */
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_STUDIO);
    }
}
