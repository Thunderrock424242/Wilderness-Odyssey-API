package com.thunder.wildernessodysseyapi.GlobalChat;

import net.minecraft.server.level.ServerLevel;

import java.io.IOException;

public class ChatClientHandler {
    private final ChatClient chatClient;

    public ChatClientHandler(String serverIp, int serverPort) throws IOException {
        this.chatClient = new ChatClient(serverIp, serverPort);
    }

    public void handleServerStart(ServerLevel server) throws IOException {
        boolean isChatEnabled = server.getGameRules().getRule(ChatGameRule.ENABLE_CHAT_CLIENT).get();
        if (isChatEnabled) {
            chatClient.connect();
        } else {
            System.out.println("Chat client disabled via game rule.");
        }
    }
}
