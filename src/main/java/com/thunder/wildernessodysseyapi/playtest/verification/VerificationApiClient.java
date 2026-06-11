package com.thunder.wildernessodysseyapi.playtest.verification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

final class VerificationApiClient {
    private static final Gson GSON = new GsonBuilder().create();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    VerificationResponse verify(String apiBaseUrl,
                                Duration timeout,
                                String code,
                                String minecraftUuid,
                                String minecraftName) {
        URI endpoint;
        try {
            endpoint = verificationEndpoint(apiBaseUrl);
        } catch (IllegalArgumentException exception) {
            return VerificationResponse.failure("Verification API URL is invalid. Set apiBaseUrl to a full http(s) URL.");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        payload.addProperty("minecraftUuid", minecraftUuid);
        payload.addProperty("minecraftName", minecraftName);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                ModConstants.LOGGER.warn("[MinecraftVerification] Bot API returned HTTP status {}.", statusCode);
                return VerificationResponse.failure(statusMessage(statusCode));
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean ok = getBoolean(body, "ok");
            if (!ok) {
                return VerificationResponse.failure(apiMessage(body, "The verification code was not accepted. Check the code and try again."));
            }
            String discordUserId = getString(body, "discordUserId");
            if (discordUserId.isBlank()) {
                return VerificationResponse.failure("The verification API did not return a Discord account id.");
            }
            String verifiedUuid = fallback(getString(body, "minecraftUuid"), minecraftUuid);
            String verifiedName = fallback(getString(body, "minecraftName"), minecraftName);
            return VerificationResponse.success(discordUserId, verifiedUuid, verifiedName);
        } catch (ConnectException exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Could not connect to verification API at {}.", endpoint);
            return VerificationResponse.failure("Could not reach the verification API. Check the API URL or whether the bot API is online.");
        } catch (HttpTimeoutException exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Verification request timed out for {}.", endpoint);
            return VerificationResponse.failure("The verification request timed out. Check the API URL or try again.");
        } catch (JsonParseException | IllegalStateException exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Verification API returned an unreadable JSON response.");
            return VerificationResponse.failure("The verification API returned an unreadable response.");
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Verification request failed: {}", exception.getMessage());
            return VerificationResponse.failure("Could not contact the verification API. Check your network and API URL.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return VerificationResponse.failure("Verification was interrupted. Please try again.");
        }
    }

    static String normalizeBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl == null) {
            return "";
        }
        String normalized = apiBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static URI verificationEndpoint(String apiBaseUrl) {
        String normalized = normalizeBaseUrl(apiBaseUrl);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Blank API base URL");
        }
        URI baseUri = URI.create(normalized);
        String scheme = baseUri.getScheme() == null ? "" : baseUri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Unsupported API base URL scheme");
        }
        if (baseUri.getHost() == null || baseUri.getHost().isBlank()) {
            throw new IllegalArgumentException("API base URL must include a host");
        }
        if (baseUri.getRawUserInfo() != null || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("API base URL must not include credentials, query parameters, or fragments");
        }
        return URI.create(normalized + "/api/minecraft/verify");
    }

    private static String getString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        try {
            return json.get(key).getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static boolean getBoolean(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return false;
        }
        try {
            return json.get(key).getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String apiMessage(JsonObject body, String fallback) {
        String message = getString(body, "message");
        if (message.isBlank()) {
            message = getString(body, "error");
        }
        return message.isBlank() ? fallback : message;
    }

    private static String statusMessage(int statusCode) {
        return switch (statusCode) {
            case 400, 404 -> "The verification code was not accepted. Check the code and try again.";
            case 401, 403 -> "The verification API rejected this request. Check the configured API URL.";
            case 408, 429 -> "The verification API is busy. Wait a moment and try again.";
            default -> "The verification API returned HTTP " + statusCode + ". Try again later.";
        };
    }

    record VerificationResponse(
            boolean ok,
            String discordUserId,
            String minecraftUuid,
            String minecraftName,
            String failureMessage
    ) {
        static VerificationResponse success(String discordUserId, String minecraftUuid, String minecraftName) {
            return new VerificationResponse(true, discordUserId, minecraftUuid, minecraftName, "");
        }

        static VerificationResponse failure(String failureMessage) {
            return new VerificationResponse(false, "", "", "", failureMessage);
        }
    }
}
