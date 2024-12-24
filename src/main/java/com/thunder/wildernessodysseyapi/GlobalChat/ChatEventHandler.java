package com.thunder.wildernessodysseyapi.GlobalChat;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

@EventBusSubscriber(modid = "yourmodid")
public class ChatEventHandler {
    private static final String WEBHOOK_URL = "https://your.webhook.url";

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        String message = String.valueOf(event.getMessage());
        String playerName = event.getPlayer().getName().getString();

        System.out.println("In-game chat detected: " + message);

        // Send message to webhook
        WebhookHelper.WebhookPayload payload = new WebhookHelper.WebhookPayload(message, playerName);
        WebhookHelper.sendWebhook(WEBHOOK_URL, payload);
    }
}
