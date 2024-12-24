package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordWebhook {
    private String content;
    private String username;
    private final String webhookUrl;

    public DiscordWebhook(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void execute() throws Exception {
        if (content == null || username == null) {
            throw new IllegalStateException("Content or username cannot be null.");
        }

        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        String payload = String.format("{\"content\":\"%s\", \"username\":\"%s\"}", content, username);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.getBytes());
            output.flush();
        }

        if (connection.getResponseCode() == 429) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        if (connection.getResponseCode() != 204) {
            throw new IllegalStateException("Unexpected response code: " + connection.getResponseCode());
        }
    }

    public static class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
