package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebhookHelper {
    public static class WebhookPayload {
        private final String content;
        private final String username;

        public WebhookPayload(String content, String username) {
            this.content = content;
            this.username = username;
        }

        public String toJson() {
            return "{\"content\":\"" + content + "\",\"username\":\"" + username + "\"}";
        }
    }

    public static void sendWebhook(String webhookUrl, WebhookPayload payload) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toJson().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Webhook sent. Response code: " + responseCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
