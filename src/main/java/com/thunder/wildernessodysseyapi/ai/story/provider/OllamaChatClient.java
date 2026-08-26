package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.ai.perf.MemoryStore;
import com.thunder.wildernessodysseyapi.ai.story.AISettings;
import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Calls a loopback Ollama chat endpoint from A.E.T.H.E.R's bounded I/O worker.
 *
 * <p>The endpoint is deliberately restricted to the local computer. A.E.T.H.E.R
 * does not send player chat or world context to a remote host.</p>
 */
public final class OllamaChatClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final AtomicLong LAST_WARNING_NANOS = new AtomicLong();
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final String MODEL_KEEP_ALIVE = "60m";

    /**
     * Loads the configured model before the player begins chatting.
     *
     * <p>Cold local model loads can take longer than an interactive chat
     * timeout. This call is intended for a fire-and-forget I/O worker during
     * integrated-server startup.</p>
     */
    public void warmUp(AISettings settings) {
        Optional<URI> chatUri = resolveChatUri(settings.getEndpoint());
        if (chatUri.isEmpty()) {
            failure("the configured endpoint is not a loopback Ollama address");
            return;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", settings.getModelName());
            payload.addProperty("stream", false);
            payload.addProperty("keep_alive", MODEL_KEEP_ALIVE);
            payload.add("messages", new JsonArray());
            HttpRequest request = HttpRequest.newBuilder(chatUri.get())
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            long startedNanos = System.nanoTime();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                failure("Ollama warm-up returned HTTP " + response.statusCode());
                return;
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            ModConstants.LOGGER.info(
                    "[Aether] Local Ollama model '{}' is ready after {} ms.",
                    settings.getModelName(),
                    elapsedMillis
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure("the local model warm-up was interrupted");
        } catch (Exception exception) {
            failure("model warm-up failed with " + exception.getClass().getSimpleName()
                    + ": " + safeMessage(exception.getMessage()));
        }
    }

    /** Requests one bounded response from the configured local Ollama model. */
    public ModelResponse generate(
            AISettings settings,
            String systemPrompt,
            String speaker,
            List<MemoryStore.ConversationMessage> history
    ) {
        Optional<URI> chatUri = resolveChatUri(settings.getEndpoint());
        if (chatUri.isEmpty()) {
            return failure("the configured endpoint is not a loopback Ollama address");
        }

        try {
            JsonObject payload = buildRequest(
                    settings.getModelName(),
                    systemPrompt,
                    history,
                    settings.getMaxOutputTokens()
            );
            HttpResponse<String> response = sendRequest(settings, chatUri.get(), payload);
            if (response.statusCode() / 100 != 2) {
                return failure("Ollama returned HTTP " + response.statusCode());
            }
            Optional<String> parsed = parseResponse(response.body(), speaker, settings.getMaxResponseCharacters());
            if (parsed.isEmpty()) {
                return failure("Ollama returned no readable dialogue");
            }
            return new ModelResponse(parsed.get(), true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure("the local model request was interrupted");
        } catch (Exception exception) {
            return failure(exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage()));
        }
    }

    /** Uses the same local model as a bounded factual reviewer for a draft reply. */
    public VerificationResponse verify(AISettings settings, String verifierPrompt) {
        Optional<URI> chatUri = resolveChatUri(settings.getEndpoint());
        if (chatUri.isEmpty()) {
            logFailure("the configured endpoint is not a loopback Ollama address");
            return new VerificationResponse(false, false);
        }

        try {
            JsonObject payload = buildVerificationRequest(settings.getModelName(), verifierPrompt);
            HttpResponse<String> response = sendRequest(settings, chatUri.get(), payload);
            if (response.statusCode() / 100 != 2) {
                logFailure("Ollama verification returned HTTP " + response.statusCode());
                return new VerificationResponse(false, false);
            }
            Optional<Boolean> approved = parseVerificationResponse(response.body());
            if (approved.isEmpty()) {
                logFailure("Ollama returned no readable verification result");
                return new VerificationResponse(false, false);
            }
            return new VerificationResponse(approved.get(), true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("the local verification request was interrupted");
            return new VerificationResponse(false, false);
        } catch (Exception exception) {
            logFailure(exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage()));
            return new VerificationResponse(false, false);
        }
    }

    static JsonObject buildRequest(
            String model,
            String systemPrompt,
            List<MemoryStore.ConversationMessage> history,
            int maxOutputTokens
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("stream", false);
        payload.addProperty("keep_alive", MODEL_KEEP_ALIVE);

        JsonArray messages = new JsonArray();
        addMessage(messages, "system", systemPrompt);
        if (history != null) {
            for (MemoryStore.ConversationMessage message : history) {
                if (message == null || message.text().isBlank()) {
                    continue;
                }
                if (message.role() == MemoryStore.Role.PLAYER) {
                    addMessage(messages, "user", message.text());
                } else {
                    addMessage(messages, "assistant", message.speaker() + ": " + message.text());
                }
            }
        }
        payload.add("messages", messages);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.65D);
        options.addProperty("num_predict", Math.max(32, Math.min(512, maxOutputTokens)));
        payload.add("options", options);
        return payload;
    }

    static JsonObject buildVerificationRequest(String model, String verifierPrompt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("stream", false);
        payload.addProperty("keep_alive", MODEL_KEEP_ALIVE);
        payload.addProperty("format", "json");

        JsonArray messages = new JsonArray();
        addMessage(messages, "system", verifierPrompt);
        addMessage(messages, "user", "Verify the candidate reply now.");
        payload.add("messages", messages);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.0D);
        options.addProperty("num_predict", 32);
        payload.add("options", options);
        return payload;
    }

    static Optional<URI> resolveChatUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return Optional.empty();
        }
        try {
            URI base = URI.create(endpoint.trim());
            String scheme = base.getScheme();
            String host = base.getHost();
            if (!"http".equalsIgnoreCase(scheme) || !isLoopbackHost(host)) {
                return Optional.empty();
            }
            String normalized = base.toString().replaceAll("/+$", "");
            if (normalized.endsWith("/api/chat")) {
                return Optional.of(URI.create(normalized));
            }
            if (normalized.endsWith("/api")) {
                return Optional.of(URI.create(normalized + "/chat"));
            }
            return Optional.of(URI.create(normalized + "/api/chat"));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> parseResponse(String json, String speaker, int maxCharacters) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement rootElement = JsonParser.parseString(json);
            if (!rootElement.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject root = rootElement.getAsJsonObject();
            JsonElement messageElement = root.get("message");
            if (messageElement == null || !messageElement.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement contentElement = messageElement.getAsJsonObject().get("content");
            if (contentElement == null || !contentElement.isJsonPrimitive()) {
                return Optional.empty();
            }
            String cleaned = sanitizeDialogue(contentElement.getAsString(), speaker, maxCharacters);
            return cleaned.isBlank() ? Optional.empty() : Optional.of(cleaned);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<Boolean> parseVerificationResponse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement outerElement = JsonParser.parseString(json);
            if (!outerElement.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement messageElement = outerElement.getAsJsonObject().get("message");
            if (messageElement == null || !messageElement.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement contentElement = messageElement.getAsJsonObject().get("content");
            if (contentElement == null || !contentElement.isJsonPrimitive()) {
                return Optional.empty();
            }
            JsonElement verdictElement = JsonParser.parseString(contentElement.getAsString());
            if (!verdictElement.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement approvedElement = verdictElement.getAsJsonObject().get("approved");
            if (approvedElement == null || !approvedElement.isJsonPrimitive()
                    || !approvedElement.getAsJsonPrimitive().isBoolean()) {
                return Optional.empty();
            }
            return Optional.of(approvedElement.getAsBoolean());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static String sanitizeDialogue(String dialogue, String speaker, int maxCharacters) {
        if (dialogue == null) {
            return "";
        }
        String cleaned = dialogue
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        cleaned = stripSpeakerPrefix(cleaned, speaker);
        int limit = Math.max(128, Math.min(2000, maxCharacters));
        if (cleaned.length() > limit) {
            int end = limit;
            if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
                end--;
            }
            cleaned = cleaned.substring(0, end).stripTrailing() + "…";
        }
        return cleaned;
    }

    private static void addMessage(JsonArray messages, String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content == null ? "" : content);
        messages.add(message);
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private static String stripSpeakerPrefix(String dialogue, String speaker) {
        if (dialogue.isBlank() || speaker == null || speaker.isBlank()) {
            return dialogue;
        }
        String plainPrefix = speaker.trim() + ":";
        String bracketPrefix = "[" + speaker.trim() + "]";
        if (dialogue.regionMatches(true, 0, plainPrefix, 0, plainPrefix.length())) {
            return dialogue.substring(plainPrefix.length()).stripLeading();
        }
        if (dialogue.regionMatches(true, 0, bracketPrefix, 0, bracketPrefix.length())) {
            return dialogue.substring(bracketPrefix.length()).stripLeading();
        }
        return dialogue;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "no additional detail";
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private static ModelResponse failure(String reason) {
        logFailure(reason);
        return new ModelResponse("", false);
    }

    private static void logFailure(String reason) {
        long now = System.nanoTime();
        long previous = LAST_WARNING_NANOS.get();
        if ((previous == 0L || now - previous >= WARNING_INTERVAL_NANOS)
                && LAST_WARNING_NANOS.compareAndSet(previous, now)) {
            ModConstants.LOGGER.warn("[Aether] Local Ollama response unavailable: {}", reason);
        }
    }

    private static HttpResponse<String> sendRequest(AISettings settings, URI uri, JsonObject payload)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Result of one local model request without exposing response internals to chat. */
    public record ModelResponse(String text, boolean successful) {
    }

    /** Result of the local factual review pass. */
    public record VerificationResponse(boolean approved, boolean successful) {
    }
}
