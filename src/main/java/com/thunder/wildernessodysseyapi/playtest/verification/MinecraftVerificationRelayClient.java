package com.thunder.wildernessodysseyapi.playtest.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;

import java.util.concurrent.CompletableFuture;

/** Preserves the Discord bot's existing wo_minecraft_verify message contract. */
final class MinecraftVerificationRelayClient {
    static final int MAX_CODE_LENGTH = 64;
    private final PlaytestWebhookClient webhook = new PlaytestWebhookClient();

    static boolean validCode(String code) {
        return code != null && code.length() >= 4 && code.length() <= MAX_CODE_LENGTH
                && code.matches("[A-Za-z0-9_-]+");
    }

    CompletableFuture<PlaytestWebhookClient.Result> sendVerification(String endpoint, int timeoutSeconds,
                                                                     String code, String uuid, String name) {
        if (!validCode(code)) {
            return CompletableFuture.completedFuture(PlaytestWebhookClient.Result.REJECTED);
        }
        JsonObject content = new JsonObject();
        content.addProperty("type", "wo_minecraft_verify");
        content.addProperty("code", code);
        content.addProperty("minecraftUuid", uuid);
        content.addProperty("minecraftName", name);
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content.toString());
        payload.addProperty("username", "Wilderness Odyssey Server");
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        return webhook.post(endpoint, timeoutSeconds, payload);
    }
}