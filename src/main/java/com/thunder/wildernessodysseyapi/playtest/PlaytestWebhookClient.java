package com.thunder.wildernessodysseyapi.playtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** Bounded asynchronous webhook transport shared by feedback and account-link delivery. */
public final class PlaytestWebhookClient {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final int MAX_RESPONSE_BYTES = 16_384;

    /** Validates an administrator-configured endpoint without including it in diagnostics. */
    public static boolean isConfigured(String endpoint) {
        try {
            endpointUri(endpoint);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static URI endpointUri(String endpoint) {
        URI uri = URI.create(endpoint == null ? "" : endpoint.trim());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid webhook endpoint");
        }
        return uri;
    }

    /** Discord must confirm the saved message; wait=false can acknowledge an unsaved message. */
    static URI deliveryUri(String endpoint) {
        URI uri = endpointUri(endpoint);
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!List.of("discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com").contains(host)) {
            return uri;
        }
        String raw = uri.toASCIIString();
        int queryStart = raw.indexOf('?');
        String base = queryStart < 0 ? raw : raw.substring(0, queryStart);
        java.util.StringJoiner query = new java.util.StringJoiner("&");
        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                String key = parameter.split("=", 2)[0];
                if (!java.net.URLDecoder.decode(key, StandardCharsets.UTF_8).equalsIgnoreCase("wait")) {
                    query.add(parameter);
                }
            }
        }
        return URI.create(base + "?" + query.add("wait=true"));
    }
    /** Posts immutable JSON with a whole-response deadline and bounded response body. */
    public CompletableFuture<Result> post(String endpoint, int timeoutSeconds, JsonObject payload) {
        if (!isConfigured(endpoint)) {
            return CompletableFuture.completedFuture(Result.NOT_CONFIGURED);
        }
        try {
            int timeout = Math.max(1, Math.min(60, timeoutSeconds));
            HttpRequest request = HttpRequest.newBuilder(deliveryUri(endpoint))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
            CompletableFuture<HttpResponse<String>> response = CLIENT.sendAsync(request, responseBodyHandler());
            // A separate future owns the deadline so cancellation reaches the original exchange.
            return response.thenApply(value -> interpret(value.statusCode(), value.body()))
                    .orTimeout(timeout, TimeUnit.SECONDS)
                    .exceptionally(error -> {
                        response.cancel(true);
                        // Exception messages/stack traces can contain the secret request URI.
                        ModConstants.LOGGER.warn("[Playtest] Webhook unavailable (timeout, network failure, or oversized response).");
                        return Result.UNAVAILABLE;
                    });
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.warn("[Playtest] Webhook request could not be started.");
            return CompletableFuture.completedFuture(Result.UNAVAILABLE);
        }
    }

    /** Accepts Discord's 204 or message receipt, and explicit JSON relay acknowledgements. */
    static Result interpret(int status, String body) {
        if (status == 204) {
            return Result.DELIVERED;
        }
        if (status < 200 || status >= 300) {
            ModConstants.LOGGER.warn("[Playtest] Webhook returned HTTP {}.", status);
            return switch (status) {
                case 400, 409, 410, 422 -> Result.REJECTED;
                case 401, 403, 404 -> Result.NOT_CONFIGURED;
                case 429 -> Result.RATE_LIMITED;
                default -> Result.UNAVAILABLE;
            };
        }
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            for (String key : List.of("success", "accepted")) {
                if (object.has(key)) {
                    if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()) {
                        return invalidResponse();
                    }
                    return object.get(key).getAsBoolean() ? Result.DELIVERED : Result.REJECTED;
                }
            }
            if (object.has("id") && object.get("id").isJsonPrimitive()
                    && object.get("id").getAsString().matches("[0-9]{1,32}")) {
                return Result.DELIVERED;
            }
        } catch (RuntimeException ignored) {
            // Untrusted response contents never enter player messages or logs.
        }
        return invalidResponse();
    }

    private static Result invalidResponse() {
        ModConstants.LOGGER.warn("[Playtest] Webhook returned an invalid acknowledgement.");
        return Result.INVALID_RESPONSE;
    }

    /** Delivery only: Discord's bot remains authoritative for actual account linking. */
    public enum Result {
        DELIVERED, NOT_CONFIGURED, REJECTED, RATE_LIMITED, UNAVAILABLE, INVALID_RESPONSE
    }

    /** Shared bounded response reader for worker-based telemetry HTTP requests. */
    public static HttpResponse.BodyHandler<String> responseBodyHandler() {
        return info -> new LimitedBody();
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if (item.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IllegalStateException("Response too large"));
                    return;
                }
                byte[] part = new byte[item.remaining()];
                item.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toString(StandardCharsets.UTF_8));
        }
    }
}