package com.thunder.wildernessodysseyapi.cloak.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.cloak.network.CloakInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CloakInputClientHandler {
    private static boolean lastSentHoldBreathState = false;
    private static boolean lastAltDown = false;

    private CloakInputClientHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            lastSentHoldBreathState = false;
            lastAltDown = false;
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        boolean leftAltDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
        boolean rightAltDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean altDown = leftAltDown || rightAltDown;
        boolean holdingBreathDown = CloakKeyMappings.HOLD_BREATH.isDown() || altDown;
        boolean cloakTogglePressed = CloakKeyMappings.CLOAK_TOGGLE.consumeClick() || (altDown && !lastAltDown);

        if (holdingBreathDown != lastSentHoldBreathState || cloakTogglePressed) {
            PacketDistributor.sendToServer(new CloakInputPayload(holdingBreathDown, cloakTogglePressed));
            lastSentHoldBreathState = holdingBreathDown;
        }
        lastAltDown = altDown;
    }
}
