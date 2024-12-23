package com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen;

import com.thunder.wildernessodysseyapi.GlobalChat.DiscordWebhook;
import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

public class CustomChatScreen extends ChatScreen {

    public CustomChatScreen() {
        super("");
    }

    @Override
    public void handleChatInput(String input, boolean addToHistory) {
        if (input.isEmpty()) return;

        // Send to local chat
        super.handleChatInput(input, addToHistory);

        // Check if the global chat game rule is enabled
        boolean enableGlobalChat = Minecraft.getInstance().level.getGameRules().getBoolean(WildernessOdysseyAPIMainModClass.ENABLE_GLOBAL_CHAT);
        if (enableGlobalChat) {
            sendToDiscord(input);
        }
    }

    private void sendToDiscord(String message) {
        DiscordWebhook webhook = new DiscordWebhook("https://discord.com/api/webhooks/1320768393111932979/3eNmHT__P2hZ3FPh7fm5oay3ire8jP83RTV7iZS5J5BJORgWYCuqS0gtj__yDzSyMJra"); // Replace with your webhook URL
        webhook.setContent(message);
        webhook.setUsername(Minecraft.getInstance().player.getName().getString());

        // Run the webhook sending asynchronously
        new Thread(() -> {
            try {
                webhook.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
