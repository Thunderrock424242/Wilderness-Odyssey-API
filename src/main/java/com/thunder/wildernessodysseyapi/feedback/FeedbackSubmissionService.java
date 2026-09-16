package com.thunder.wildernessodysseyapi.feedback;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;

import java.util.concurrent.CompletableFuture;

/** Creates feedback payloads from server-thread snapshots and reports confirmed HTTP outcomes. */
final class FeedbackSubmissionService {
    private final PlaytestWebhookClient webhook = new PlaytestWebhookClient();

    CompletableFuture<PlaytestWebhookClient.Result> submit(String name, String uuid, String message,
                                                            FeedbackConfig.FeedbackConfigValues config) {
        if (!config.enabled()) {
            return CompletableFuture.completedFuture(PlaytestWebhookClient.Result.NOT_CONFIGURED);
        }
        JsonObject payload = new JsonObject();
        // An embed reserves Discord's content limit for the configured 2000-character message.
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Player feedback");
        embed.addProperty("description", message);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", name + " (" + uuid + ")");
        embed.add("footer", footer);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        return webhook.post(config.webhookUrl(), config.requestTimeoutSeconds(), payload);
    }
}