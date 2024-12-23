package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordWebhook {

    private final String webhookUrl;
    private String content;
    private String username;

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
        if (this.content == null || this.content.isEmpty()) {
            throw new IllegalArgumentException("Content must not be null or empty");
        }

        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        String payload = String.format("{\"content\":\"%s\", \"username\":\"%s\"}", content, username);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes());
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 204) {
            throw new RuntimeException("Failed to send Discord webhook. Response code: " + responseCode);
        }
    }
}
