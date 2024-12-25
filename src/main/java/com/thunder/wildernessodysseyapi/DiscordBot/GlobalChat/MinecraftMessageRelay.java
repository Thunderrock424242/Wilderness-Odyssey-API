package com.thunder.wildernessodysseyapi.DiscordBot.GlobalChat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

public class MinecraftMessageRelay {
    private static MinecraftServer server;

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static void sendToMinecraft(String message) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        }
    }
}
