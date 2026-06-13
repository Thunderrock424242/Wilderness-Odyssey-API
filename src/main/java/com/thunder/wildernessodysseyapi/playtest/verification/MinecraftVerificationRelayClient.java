package com.thunder.wildernessodysseyapi.playtest.verification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class MinecraftVerificationRelayClient {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String RELAY_TYPE = "wo_minecraft_verify";
    private static final String WEBHOOK_USERNAME = "Wilderness Odyssey Server";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    CompletableFuture<RelayResult> sendVerification(String webhookUrl,
                                                    int timeoutSeconds,
                                                    String code,
                                                    String minecraftUuid,
                                                    String minecraftName) {
        URI webhookUri;
        try {
            webhookUri = parseWebhookUri(webhookUrl);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(RelayResult.failure("Verification relay webhook is invalid. Please tell staff to check the server config."));
        }

        JsonObject content = new JsonObject();
        content.addProperty("type", RELAY_TYPE);
        content.addProperty("code", code);
        content.addProperty("minecraftUuid", minecraftUuid);
        content.addProperty("minecraftName", minecraftName);

        JsonObject webhookPayload = new JsonObject();
        webhookPayload.addProperty("content", GSON.toJson(content));
        webhookPayload.addProperty("username", WEBHOOK_USERNAME);

        HttpRequest request = HttpRequest.newBuilder(webhookUri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(webhookPayload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        return RelayResult.success();
                    }
                    ModConstants.LOGGER.warn("[MinecraftVerification] Verification relay webhook returned HTTP status {}.", status);
                    return RelayResult.failure("Verification relay could not be sent. Please try again or tell staff.");
                })
                .exceptionally(exception -> {
                    ModConstants.LOGGER.warn("[MinecraftVerification] Verification relay webhook request failed: {}", exception.getClass().getSimpleName());
                    return RelayResult.failure("Verification relay is unavailable right now. Please try again or tell staff.");
                });
    }

    private static URI parseWebhookUri(String webhookUrl) {
        URI uri = URI.create(webhookUrl == null ? "" : webhookUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("Webhook URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Webhook URL must include a host and no credentials");
        }
        return uri;
    }

    record RelayResult(boolean sent, String message) {
        static RelayResult success() {
            return new RelayResult(true, "");
        }

        static RelayResult failure(String message) {
            return new RelayResult(false, message);
        }
    }
}
