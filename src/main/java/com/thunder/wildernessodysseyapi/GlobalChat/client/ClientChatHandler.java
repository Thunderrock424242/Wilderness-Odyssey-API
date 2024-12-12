package com.thunder.wildernessodysseyapi.GlobalChat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen.CustomChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(value = Dist.CLIENT, modid = MOD_ID)
public class ClientChatHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().isSameThread()) {
            return; // Ensure this runs on the client thread
        }

        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // Check if the current screen is the default ChatScreen
        if (currentScreen instanceof ChatScreen && !(currentScreen instanceof CustomChatScreen)) {
            mc.setScreen(new CustomChatScreen());
        }
    }
}