package com.thunder.wildernessodysseyapi.GlobalChat;

import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebhookHelper {

    public static void sendMessage(String message) {
        String webhookUrl = WildernessOdysseyAPIMainModClass.WEBHOOK_URL; // Access URL from main class

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            System.err.println("Webhook URL is not configured!");
            return;
        }

        try {
            // Create connection
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            // Create JSON payload
            String jsonPayload = "{ \"content\": \"" + message + "\" }";

            // Write payload to request body
            OutputStream os = connection.getOutputStream();
            os.write(jsonPayload.getBytes());
            os.flush();
            os.close();

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.err.println("Failed to send message: " + responseCode);
            }

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
