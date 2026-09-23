package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.ai.story.AIBackendConfig;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Server-owned Aether API transport. A single deadline covers retries and body reading.
 * Access denial by a hosting proxy, overload and timeouts fail promptly into the caller's fallback.
 * This class never launches processes and never sends requests to Ollama.
 */
public final class AetherBackendClient implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private static final int MAX_BODY_BYTES = 65_536;
    private final AIBackendConfig config;
    private final HttpClient http;
    private final Semaphore permits;
    private final BooleanSupplier networkAllowed;
    private final LongSupplier clock;
    private final java.util.Set<CompletableFuture<?>> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile BackendStatus status;
    private long retryAfterNanos;
    private boolean probing;

    /** Creates an off-thread client for server-side callers with their own scheduling. */
    public AetherBackendClient(AIBackendConfig config) {
        this(config, () -> true);
    }

    /** The network guard lets the game owner reject main-thread calls before any I/O. */
    public AetherBackendClient(AIBackendConfig config, BooleanSupplier networkAllowed) {
        this(config, networkAllowed, System::nanoTime);
    }

    AetherBackendClient(AIBackendConfig config, BooleanSupplier networkAllowed, LongSupplier clock) {
        this.config = config;
        this.networkAllowed = networkAllowed;
        this.clock = clock;
        this.permits = new Semaphore(config.maxConcurrentRequests());
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.status = snapshot(false, false, "", -1, config.enabled() ? "NOT_CHECKED" : "DISABLED");
    }

    /** Pure cached status accessor, safe for diagnostics on the game thread. */
    public BackendStatus status() {
        return status;
    }

    /** Executes one request on a worker; failures return typed data without exception details. */
    public ModelResponse generate(AetherRequest request, List<String> allowedSpeakers, int maxCharacters) {
        if (!canUseNetwork()) {
            return ModelResponse.failure("DISABLED");
        }
        if (!permits.tryAcquire()) {
            return ModelResponse.failure("CLIENT_BUSY");
        }
        boolean admitted = false;
        long started = clock.getAsLong();
        try {
            if (!admitCircuit()) {
                return ModelResponse.failure("CIRCUIT_OPEN");
            }
            admitted = true;
            byte[] body = JSON.toJson(request).getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_BODY_BYTES) {
                succeedCircuit();
                return ModelResponse.failure("REQUEST_TOO_LARGE");
            }
            long deadline = started + TimeUnit.SECONDS.toNanos(config.timeoutSeconds());
            for (int attempt = 0; attempt < config.retryAttempts(); attempt++) {
                HttpResponse<byte[]> response;
                try {
                    response = exchange("/v1/aether/generate", body, deadline);
                } catch (TimeoutException exception) {
                    return failed("TIMEOUT", false, started);
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (exception.getCause() instanceof java.net.http.HttpTimeoutException) {
                        return failed("TIMEOUT", false, started);
                    }
                    if (isConnectionFailure(exception.getCause()) && attempt + 1 < config.retryAttempts()
                            && pauseBeforeRetry(attempt, deadline)) {
                        continue;
                    }
                    return failed("BACKEND_UNAVAILABLE", false, started);
                }
                int code = response.statusCode();
                if (code == 200) {
                    Optional<ModelResponse> parsed = parseResponse(
                            response.body(), request, allowedSpeakers, maxCharacters);
                    if (parsed.isPresent()) {
                        ModelResponse reply = parsed.get();
                        status = snapshot(true, true, reply.model(), elapsed(started), "");
                        succeedCircuit();
                        return reply;
                    }
                    return failed("INVALID_RESPONSE", true, started);
                }
                // Retrying inference after a timeout or overload may double GPU work.
                // Only an explicit temporary gateway failure is retried, within the same deadline.
                if ((code == 502 || code == 504) && attempt + 1 < config.retryAttempts()
                        && pauseBeforeRetry(attempt, deadline)) {
                    continue;
                }
                return failed(switch (code) {
                    case 401, 403 -> "UNAUTHORIZED";
                    case 429, 503 -> "BACKEND_BUSY_OR_UNAVAILABLE";
                    case 408, 504 -> "TIMEOUT";
                    default -> "BACKEND_ERROR";
                }, true, started);
            }
            return failed("BACKEND_UNAVAILABLE", false, started);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed("INTERRUPTED", false, started);
        } catch (RuntimeException exception) {
            return failed("INVALID_RESPONSE_OR_CONFIGURATION", false, started);
        } finally {
            if (admitted) {
                finishProbe();
            }
            permits.release();
        }
    }

    /** Checks liveness and readiness on a worker without changing the generation circuit. */
    public BackendStatus checkHealth() {
        if (!canUseNetwork() || !permits.tryAcquire()) {
            return status;
        }
        long started = clock.getAsLong();
        boolean reachable = false;
        try {
            long deadline = started + TimeUnit.SECONDS.toNanos(Math.min(5, config.timeoutSeconds()));
            HttpResponse<byte[]> health = exchange("/health", null, deadline);
            reachable = health.statusCode() == 200;
            if (!reachable) {
                status = snapshot(false, false, "", elapsed(started), "BACKEND_UNAVAILABLE");
                return status;
            }
            HttpResponse<byte[]> ready = exchange("/ready", null, deadline);
            JsonObject result = jsonObject(ready.body());
            boolean modelReady = ready.statusCode() == 200
                    && ((result.has("ready") && result.get("ready").isJsonPrimitive()
                    && result.get("ready").getAsJsonPrimitive().isBoolean() && result.get("ready").getAsBoolean())
                    || ("UP".equals(string(result, "status")) && "UP".equals(string(result, "ollama"))));
            status = snapshot(true, modelReady, string(result, "model"), elapsed(started),
                    modelReady ? "" : (ready.statusCode() == 401 ? "UNAUTHORIZED" : "MODEL_NOT_READY"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            status = snapshot(reachable, false, "", elapsed(started), "INTERRUPTED");
        } catch (Exception exception) {
            status = snapshot(reachable, false, "", elapsed(started), "BACKEND_UNAVAILABLE");
        } finally {
            permits.release();
        }
        return status;
    }

    private boolean canUseNetwork() {
        return !closed && config.enabled()
                && networkAllowed.getAsBoolean() && baseUri(config.baseUrl()).isPresent();
    }

    private HttpResponse<byte[]> exchange(String path, byte[] body, long deadline)
            throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
        if (closed || !networkAllowed.getAsBoolean()) {
            throw new TimeoutException("CANCELLED");
        }
        long remaining = deadline - clock.getAsLong();
        if (remaining <= 0) {
            throw new TimeoutException("DEADLINE");
        }
        URI base = baseUri(config.baseUrl()).orElseThrow();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base.toString() + path))
                .timeout(Duration.ofNanos(remaining)).header("Accept", "application/json");
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        }
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(builder.build(),
                ignored -> new BoundedResponseBody(MAX_BODY_BYTES));
        pending.add(future);
        if (closed) {
            future.cancel(true);
        }
        try {
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } finally {
            pending.remove(future);
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static boolean isConnectionFailure(Throwable failure) {
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof java.net.ConnectException) {
                return true;
            }
        }
        return false;
    }

    private boolean pauseBeforeRetry(int attempt, long deadline) throws InterruptedException {
        long delay = TimeUnit.MILLISECONDS.toNanos((long) config.retryBackoffMillis() * (attempt + 1));
        if (closed || deadline - clock.getAsLong() <= delay + TimeUnit.MILLISECONDS.toNanos(10)) {
            return false;
        }
        TimeUnit.NANOSECONDS.sleep(delay);
        return true;
    }

    private synchronized boolean admitCircuit() {
        if (retryAfterNanos == 0) {
            return true;
        }
        if (clock.getAsLong() - retryAfterNanos < 0 || probing) {
            return false;
        }
        probing = true;
        return true;
    }

    private synchronized void succeedCircuit() {
        retryAfterNanos = 0;
        probing = false;
    }

    private synchronized void finishProbe() {
        probing = false;
    }

    private ModelResponse failed(String error, boolean reachable, long started) {
        synchronized (this) {
            retryAfterNanos = clock.getAsLong() + TimeUnit.SECONDS.toNanos(config.circuitCooldownSeconds());
        }
        status = snapshot(reachable, false, "", elapsed(started), error);
        return ModelResponse.failure(error);
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, clock.getAsLong() - started));
    }

    private BackendStatus snapshot(boolean reachable, boolean ready, String model, long latency, String error) {
        return new BackendStatus(config.enabled(), config.mode(),
                baseUri(config.baseUrl()).map(URI::toString).orElse(""), reachable, ready, model, latency, error);
    }

    /** Resolves an operator-controlled HTTP(S) base without credentials or query-string leakage. */
    static Optional<URI> baseUri(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) {
                return Optional.empty();
            }
            String path = uri.getPath();
            if (path != null && (path.contains("..") || path.contains("/api"))) {
                return Optional.empty();
            }
            return Optional.of(URI.create(uri.toString().replaceAll("/+$", "")));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static Optional<ModelResponse> parseResponse(byte[] bytes, AetherRequest request,
                                                  List<String> speakers, int maxCharacters) {
        try {
            JsonObject root = jsonObject(bytes);
            if (!root.has("success") || !root.get("success").getAsJsonPrimitive().isBoolean()
                    || !root.get("success").getAsBoolean()
                    || !request.requestId().equals(string(root, "requestId"))) {
                return Optional.empty();
            }
            String selected = string(root, "speaker");
            String speaker = speakers.stream().filter(name -> name.equalsIgnoreCase(selected)).findFirst().orElse("");
            if (speaker.isEmpty() || (!request.speaker().isBlank() && !request.speaker().equalsIgnoreCase(speaker))) {
                return Optional.empty();
            }
            String display = clean(string(root, "response"), maxCharacters);
            if (display.isBlank()) {
                return Optional.empty();
            }
            String speech = clean(string(root, "speech"), maxCharacters);
            float radio = root.has("radioEffect") ? root.get("radioEffect").getAsFloat() : 0.0F;
            radio = Float.isFinite(radio) ? Math.max(0.0F, Math.min(0.35F, radio)) : 0.0F;
            return Optional.of(new ModelResponse(true, speaker, display, speech,
                    VoiceEmotion.fromModelValue(string(root, "emotion")), radio,
                    clean(string(root, "model"), 128), ""));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static JsonObject jsonObject(byte[] bytes) {
        String value = new String(bytes, StandardCharsets.UTF_8);
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quoted) {
                if (escaped) { escaped = false; }
                else if (c == '\\') { escaped = true; }
                else if (c == '"') { quoted = false; }
            } else if (c == '"') { quoted = true; }
            else if (c == '{' || c == '[') {
                if (++depth > 32) { throw new IllegalArgumentException("JSON_TOO_DEEP"); }
            } else if (c == '}' || c == ']') { depth--; }
        }
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isString() ? object.get(key).getAsString() : "";
    }

    private static String clean(String value, int limit) {
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        int end = Math.min(cleaned.length(), Math.max(1, Math.min(2000, limit)));
        if (end > 0 && Character.isHighSurrogate(cleaned.charAt(end - 1))) {
            end--;
        }
        return cleaned.substring(0, end);
    }

    /** Cancels this server session's outstanding exchanges; never touches any external process. */
    @Override
    public void close() {
        closed = true;
        pending.forEach(future -> future.cancel(true));
        http.shutdownNow();
    }

    /** Network result with a stable failure code and sanitized, speaker-validated dialogue. */
    public record ModelResponse(boolean successful, String speaker, String displayText, String speechText,
                                VoiceEmotion emotion, float radioEffect, String model, String error) {
        static ModelResponse failure(String error) {
            return new ModelResponse(false, "", "", "", VoiceEmotion.NORMAL, 0.0F, "", error);
        }
    }
}
