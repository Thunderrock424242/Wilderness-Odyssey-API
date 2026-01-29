package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

public final class TelemetryPayloads {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().create();

    private TelemetryPayloads() {
    }

    public static HttpRequest buildRequest(String webhookUrl, int timeoutSeconds, JsonObject payload) {
        String body = payload == null ? "{}" : GSON.toJson(payload);
        return HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
