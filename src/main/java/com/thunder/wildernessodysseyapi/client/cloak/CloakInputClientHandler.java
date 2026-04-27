package com.thunder.wildernessodysseyapi.client.cloak;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.network.CloakInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CloakInputClientHandler {
    private static boolean lastSentAltState = false;

    private CloakInputClientHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            lastSentAltState = false;
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        boolean rightAltDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean altDown = CloakKeyMappings.CLOAK_ALT.isDown() || rightAltDown;

        if (altDown != lastSentAltState) {
            PacketDistributor.sendToServer(new CloakInputPayload(altDown));
            lastSentAltState = altDown;
        }
    }
}
