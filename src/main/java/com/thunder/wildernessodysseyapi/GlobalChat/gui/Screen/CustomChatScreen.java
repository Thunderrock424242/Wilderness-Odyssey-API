package com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen;

import com.thunder.wildernessodysseyapi.GlobalChat.DiscordWebhook;
import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

        // Check if the global chat game rule is enabled
        boolean enableGlobalChat = Minecraft.getInstance().level.getGameRules()
                .getBoolean(WildernessOdysseyAPIMainModClass.ENABLE_GLOBAL_CHAT);

        if (enableGlobalChat) {
            sendToDiscord(input);
        }

        // Always send to local chat
        super.handleChatInput(input, addToHistory);
    }

    private void sendToDiscord(String message) {
        String webhookUrl = "https://discord.com/api/webhooks/1320768393111932979/3eNmHT__P2hZ3FPh7fm5oay3ire8jP83RTV7iZS5J5BJORgWYCuqS0gtj__yDzSyMJra"; // Replace with your webhook URL
        DiscordWebhook webhook = new DiscordWebhook(webhookUrl);
        webhook.setContent(message);
        webhook.setUsername(Minecraft.getInstance().player.getName().getString());

        // Run the webhook sending asynchronously with enhanced error handling
        new Thread(() -> {
            try {
                webhook.execute();
                // Provide feedback to the player via the in-game chat
                displayFeedbackToPlayer("Message sent to global chat!");
                LOGGER.info("Message successfully sent to Discord.");
            } catch (DiscordWebhook.RateLimitException e) {
                LOGGER.warn("Rate limit hit: Message not sent.", e);
                displayFeedbackToPlayer("You're sending messages too fast! Please wait.");
            } catch (Exception e) {
                LOGGER.error("Failed to send message to Discord:", e);
                displayFeedbackToPlayer("Failed to send message to global chat. Please try again later.");
            }
        }).start();
    }

    private void displayFeedbackToPlayer(String feedback) {
        MutableComponent feedbackComponent = Component.literal(feedback);
        Minecraft.getInstance().gui.getChat().addMessage(feedbackComponent);
    }
}
