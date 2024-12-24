package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordWebhook {

    private static final long MIN_MESSAGE_DELAY_MS = 2000; // 2 seconds
    private static long lastMessageTimestamp = 0;

    private final String webhookUrl;
    private String content;
    private String username;
    private String avatarUrl;

    public DiscordWebhook(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void execute() throws Exception {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMessageTimestamp < MIN_MESSAGE_DELAY_MS) {
            throw new RateLimitException("Rate limit exceeded. Please wait before sending another message.");
        }
        lastMessageTimestamp = currentTime;

        if (this.content == null || this.content.isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }

        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        String payload = String.format(
                "{\"content\":\"%s\",\"username\":\"%s\",\"avatar_url\":\"%s\"}",
                content,
                username == null ? "Minecraft Player" : username,
                avatarUrl == null ? "" : avatarUrl
        );

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes());
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 429) {
            throw new RateLimitException("Discord rate limit reached.");
        } else if (responseCode != 204) {
            throw new RuntimeException("Failed to send Discord webhook. Response code: " + responseCode);
        }
    }

    public static class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
