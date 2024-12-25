package com.thunder.wildernessodysseyapi.DiscordBot.GlobalChat;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

public class MinecraftChatListener {
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String playerName = event.getUsername();
        String message = String.valueOf(event.getMessage());

        // Format message for Discord
        String discordMessage = playerName + ": " + message;
        DiscordBot.sendMessageToDiscord(discordMessage);
    }
}
