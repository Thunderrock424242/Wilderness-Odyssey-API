package com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen;

import com.thunder.wildernessodysseyapi.GlobalChat.DiscordWebhook;
import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomChatScreen extends ChatScreen {

    private static final Logger LOGGER = LogManager.getLogger();

    public CustomChatScreen() {
        super("");
    }

    @Override
    public void handleChatInput(String input, boolean addToHistory) {
        if (input.isEmpty() || input.startsWith("/")) {
            LOGGER.info("Message ignored: empty or a command.");
            return; // Skip empty messages and commands
        }

        // Send to local chat
        super.handleChatInput(input, addToHistory);

        // Check if the global chat game rule is enabled
        boolean enableGlobalChat = Minecraft.getInstance().level.getGameRules()
                .getBoolean(WildernessOdysseyAPIMainModClass.ENABLE_GLOBAL_CHAT);
        if (enableGlobalChat) {
            sendToDiscord(input);
        }
    }

    private void sendToDiscord(String message) {
        String webhookUrl = "YOUR_DISCORD_WEBHOOK_URL"; // Replace with your webhook URL
        DiscordWebhook webhook = new DiscordWebhook(webhookUrl);
        webhook.setContent(message);
        webhook.setUsername(Minecraft.getInstance().player.getName().getString());

        // Run the webhook sending asynchronously with enhanced error handling
        new Thread(() -> {
            try {
                webhook.execute();
                // Provide feedback to the player
                Minecraft.getInstance().player.sendMessage(
                        Component.literal("Message sent to global chat!"), Minecraft.getInstance().player.getUUID());
                LOGGER.info("Message successfully sent to Discord.");
            } catch (DiscordWebhook.RateLimitException e) {
                LOGGER.warn("Rate limit hit: Message not sent.", e);
                Minecraft.getInstance().player.sendMessage(
                        Component.literal("You're sending messages too fast! Please wait."), Minecraft.getInstance().player.getUUID());
            } catch (Exception e) {
                LOGGER.error("Failed to send message to Discord:", e);
                Minecraft.getInstance().player.sendMessage(
                        Component.literal("Failed to send message to global chat. Please try again later."), Minecraft.getInstance().player.getUUID());
            }
        }).start();
    }
}
