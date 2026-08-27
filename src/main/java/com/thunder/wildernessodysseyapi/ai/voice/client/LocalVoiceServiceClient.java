package com.thunder.wildernessodysseyapi.ai.voice.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import net.minecraft.SharedConstants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous, loopback-only transport for the separately started Python voice service. */
final class LocalVoiceServiceClient {
    private static final int MAX_WAV_RESPONSE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_JSON_RESPONSE_BYTES = 64 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    CompletableFuture<String> transcribe(byte[] wav) {
        Optional<URI> endpoint = resolvePath(AetherVoiceConfig.SERVICE_ENDPOINT.get(), "/v1/transcribe");
        if (endpoint.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("voice endpoint is not loopback-only"));
        }
        HttpRequest.Builder builder = baseRequest(endpoint.get())
                .header("Content-Type", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofByteArray(wav));
        return HTTP_CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    String body = boundedJsonBody(response.statusCode(), response.body());
                    requireSuccess(response.statusCode(), body);
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonElement text = root.get("text");
                    if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) {
                        throw new IllegalStateException("voice service returned no transcription");
                    }
                    return boundTranscript(text.getAsString());
                });
    }

    CompletableFuture<byte[]> speak(VoiceLine line) {
        Optional<URI> endpoint = resolvePath(AetherVoiceConfig.SERVICE_ENDPOINT.get(), "/v1/speak");
        if (endpoint.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("voice endpoint is not loopback-only"));
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("text", line.speechText());
        payload.addProperty("voice", AetherVoiceConfig.VOICE_NAME.get());
        payload.addProperty("speed", AetherVoiceConfig.SPEECH_SPEED.get());
        payload.addProperty("emotion", line.emotion().wireName());
        payload.addProperty("radio_effect", AetherVoiceConfig.RADIO_PROCESSING.get() ? line.radioEffect() : 0.0F);
        payload.addProperty("effects_enabled", AetherVoiceConfig.RADIO_PROCESSING.get());

        HttpRequest request = baseRequest(endpoint.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    requireSuccess(response.statusCode(), "binary response");
                    byte[] body = response.body();
                    if (body.length == 0 || body.length > MAX_WAV_RESPONSE_BYTES) {
                        throw new IllegalStateException("voice service returned invalid audio size");
                    }
                    return body;
                });
    }

    CompletableFuture<ServiceStatus> status() {
        Optional<URI> endpoint = resolvePath(AetherVoiceConfig.SERVICE_ENDPOINT.get(), "/v1/status");
        if (endpoint.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("voice endpoint is not loopback-only"));
        }
        HttpRequest request = baseRequest(endpoint.get()).GET().build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    String body = boundedJsonBody(response.statusCode(), response.body());
                    requireSuccess(response.statusCode(), body);
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonObject latency = root.has("latency_ms") && root.get("latency_ms").isJsonObject()
                            ? root.getAsJsonObject("latency_ms") : new JsonObject();
                    return new ServiceStatus(
                            stringValue(root, "model_state", "unknown"),
                            booleanValue(root, "speech_recognition_ready"),
                            booleanValue(root, "text_to_speech_ready"),
                            stringValue(root, "device", "unknown"),
                            stringValue(root, "voice_model", "unknown"),
                            numberValue(latency, "stt"),
                            numberValue(latency, "tts"),
                            stringValue(root, "last_error", "")
                    );
                });
    }

    static Optional<URI> resolvePath(String root, String path) {
        if (root == null || root.isBlank() || path == null || !path.startsWith("/")) {
            return Optional.empty();
        }
        try {
            URI base = URI.create(root.trim());
            if (!"http".equalsIgnoreCase(base.getScheme()) || !isLoopback(base.getHost())
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                return Optional.empty();
            }
            String normalized = base.toString().replaceAll("/+$", "");
            return Optional.of(URI.create(normalized + path));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static String boundTranscript(String transcript) {
        String cleaned = transcript == null ? "" : transcript.replace("\u0000", "").trim();
        if (cleaned.length() <= SharedConstants.MAX_CHAT_LENGTH) {
            return cleaned;
        }
        int end = SharedConstants.MAX_CHAT_LENGTH;
        if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
            end--;
        }
        return cleaned.substring(0, end).stripTrailing();
    }

    private static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private static HttpRequest.Builder baseRequest(URI endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(AetherVoiceConfig.REQUEST_TIMEOUT_SECONDS.get()))
                .header("Accept", "application/json, audio/wav");
        String token = AetherVoiceConfig.SERVICE_TOKEN.get().trim();
        if (!token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private static void requireSuccess(int statusCode, String response) {
        if (statusCode / 100 != 2) {
            String detail = response == null ? "" : response.replaceAll("\\s+", " ").trim();
            if (detail.length() > 180) {
                detail = detail.substring(0, 180);
            }
            throw new IllegalStateException("voice service HTTP " + statusCode
                    + (detail.isEmpty() ? "" : ": " + detail));
        }
    }

    private static String boundedJsonBody(int statusCode, byte[] body) {
        if (statusCode / 100 != 2 && (body == null || body.length == 0)) {
            return "";
        }
        if (statusCode / 100 != 2 && body.length > MAX_JSON_RESPONSE_BYTES) {
            return "response body exceeded local limit";
        }
        if (body == null || body.length == 0 || body.length > MAX_JSON_RESPONSE_BYTES) {
            throw new IllegalStateException("voice service returned an invalid JSON response size");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String stringValue(JsonObject object, String name, String fallback) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static double numberValue(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : -1.0D;
    }

    record ServiceStatus(
            String modelState,
            boolean speechRecognitionReady,
            boolean textToSpeechReady,
            String device,
            String voiceModel,
            double lastSttMilliseconds,
            double lastTtsMilliseconds,
            String lastError
    ) {
    }
}
