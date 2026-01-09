package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thunder.wildernessodysseyapi.Core.ModConstants;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LocalModelClient {

    private final HttpClient httpClient;
    private final Gson gson;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;

    public LocalModelClient(String baseUrl, String model, Duration timeout) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new GsonBuilder().create();
        this.endpoint = URI.create(baseUrl + "/api/generate");
        this.model = model;
        this.timeout = timeout;
    }

    public Optional<String> generateReply(String systemPrompt, String playerMessage, String context) {
        String prompt = buildPrompt(systemPrompt, playerMessage, context);
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ModConstants.LOGGER.warn("Local model request failed with status {}", response.statusCode());
                return Optional.empty();
            }
            OllamaResponse parsed = gson.fromJson(response.body(), OllamaResponse.class);
            if (parsed == null || parsed.response == null || parsed.response.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parsed.response.trim());
        } catch (ConnectException e) {
            ModConstants.LOGGER.warn("Local model request failed: unable to connect to {}.", endpoint);
            return Optional.empty();
        } catch (HttpTimeoutException e) {
            ModConstants.LOGGER.warn("Local model request timed out for {}.", endpoint);
            return Optional.empty();
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Local model request failed.", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            ModConstants.LOGGER.warn("Local model request interrupted.", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private String buildPrompt(String systemPrompt, String playerMessage, String context) {
        StringBuilder builder = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.append(systemPrompt.trim()).append("\n\n");
        }
        if (context != null && !context.isBlank()) {
            builder.append("Conversation so far:\n").append(context.trim()).append("\n\n");
        }
        builder.append("Player: ").append(playerMessage == null ? "" : playerMessage.trim()).append("\n");
        builder.append("AI:");
        return builder.toString();
    }

    private static class OllamaResponse {
        private String response;
    }
}
