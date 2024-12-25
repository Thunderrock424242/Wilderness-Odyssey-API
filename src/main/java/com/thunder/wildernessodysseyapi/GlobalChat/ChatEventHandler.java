package com.thunder.wildernessodysseyapi.GlobalChat;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;

public class ChatEventHandler {
    private ChatClient chatClient;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            chatClient = new ChatClient("209.192.200.84", 25582); // Replace with actual server IP and port
            chatClient.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (chatClient != null) {
            chatClient.close();
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        String message = String.valueOf(event.getMessage());
        if (chatClient != null) {
            chatClient.sendMessage(message);
        }
    }
}
