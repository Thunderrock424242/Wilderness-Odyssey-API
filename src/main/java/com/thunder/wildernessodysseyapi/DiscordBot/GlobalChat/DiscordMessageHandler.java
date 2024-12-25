package com.thunder.wildernessodysseyapi.DiscordBot.GlobalChat;

public class DiscordMessageHandler {
    private static final String WEBHOOK_URL = "https://your.webhook.url";

    public static void onDiscordMessageSend(String message, String channel) {
        System.out.println("Outbound Discord message: " + message);

        // Send message to webhook
        WebhookHelper.WebhookPayload payload = new WebhookHelper.WebhookPayload(message, "Discord (" + channel + ")");
        WebhookHelper.sendWebhook(WEBHOOK_URL, payload);
    }
}
