package com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen;

import com.thunder.wildernessodysseyapi.GlobalChat.DiscordWebhook;
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

        // Also send to Discord global chat via webhook
        sendToDiscord(input);
    }

    private void sendToDiscord(String message) {
        DiscordWebhook webhook = new DiscordWebhook("YOUR_DISCORD_WEBHOOK_URL"); // Replace with your webhook URL
        webhook.setContent(message);
        webhook.setUsername(net.minecraft.client.Minecraft.getInstance().player.getName().getString());

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
