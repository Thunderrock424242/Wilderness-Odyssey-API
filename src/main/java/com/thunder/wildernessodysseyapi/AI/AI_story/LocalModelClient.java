package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

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
    private final CircuitBreaker circuitBreaker;

    public LocalModelClient(String baseUrl, String model, Duration timeout) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new GsonBuilder().create();
        this.endpoint = URI.create(baseUrl + "/api/generate");
        this.model = model;
        this.timeout = timeout;
        this.circuitBreaker = CircuitBreaker.of("atlas-local-model", CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(4)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .build());
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
            Optional<OllamaResponse> parsedResponse = executeJsonRequest(request, OllamaResponse.class);
            if (parsedResponse.isEmpty()) {
                return Optional.empty();
            }
            OllamaResponse parsed = parsedResponse.get();
            if (parsed == null || parsed.response == null || parsed.response.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parsed.response.trim());
        } catch (CallNotPermittedException e) {
            ModConstants.LOGGER.warn("Local model circuit breaker is open; skipping request for {}.", endpoint);
            return Optional.empty();
        }
    }

    public boolean isReachable() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.resolve("/api/tags").toString()))
                .timeout(timeout)
                .GET()
                .build();
        try {
            Optional<Object> response = executeJsonRequest(request, Object.class);
            return response.isPresent();
        } catch (CallNotPermittedException e) {
            ModConstants.LOGGER.debug("Local model probe skipped because circuit breaker is open.");
            return false;
        }
    }

    private <T> Optional<T> executeJsonRequest(HttpRequest request, Class<T> responseType) {
        long startNanos = System.nanoTime();
        if (!circuitBreaker.tryAcquirePermission()) {
            throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        }
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsedNanos = System.nanoTime() - startNanos;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                circuitBreaker.onError(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS,
                        new IOException("HTTP " + response.statusCode()));
                ModConstants.LOGGER.warn("Local model request failed with status {}", response.statusCode());
                return Optional.empty();
            }
            circuitBreaker.onSuccess(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
            T parsed = gson.fromJson(response.body(), responseType);
            return Optional.ofNullable(parsed);
        } catch (ConnectException e) {
            circuitBreaker.onError(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS, e);
            ModConstants.LOGGER.warn("Local model request failed: unable to connect to {}.", endpoint);
            return Optional.empty();
        } catch (HttpTimeoutException e) {
            circuitBreaker.onError(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS, e);
            ModConstants.LOGGER.warn("Local model request timed out for {}.", endpoint);
            return Optional.empty();
        } catch (IOException e) {
            circuitBreaker.onError(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS, e);
            ModConstants.LOGGER.warn("Local model request failed.", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            circuitBreaker.onError(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS, e);
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
