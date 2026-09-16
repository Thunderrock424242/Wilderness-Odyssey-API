package com.thunder.aether.server.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounded JSON transport primitives; error responses never include exception or credential text. */
public final class JsonHttp {
    public static final Gson JSON = new Gson();
    private JsonHttp() {}

    /** Rejects excessive nesting before parsing any untrusted JSON. */
    public static JsonObject object(byte[] bytes) {
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

    /** Writes one bounded application response and closes the exchange. */
    public static void send(HttpExchange exchange, int code, Object body) {
        byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(code, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        } catch (IOException ignored) {
            // A disconnected/expired peer cannot receive an error; do not log its payload.
        } finally { exchange.close(); }
    }

    /** Operator URLs allow HTTP(S), with no embedded credentials, query or fragment. */
    public static URI baseUri(String value) {
        URI uri = URI.create(value);
        if (!(("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null
                && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535))) {
            throw new IllegalArgumentException("INVALID_ENDPOINT");
        }
        return URI.create(uri.toString().replaceAll("/+$", ""));
    }

    /** Cancels oversized responses before unbounded response accumulation. */
    public static final class Body implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> future = new CompletableFuture<>();
        private Flow.Subscription subscription;
        public Body(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return future; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription; subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    future.completeExceptionally(new IOException("RESPONSE_TOO_LARGE")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { future.completeExceptionally(error); }
        @Override public void onComplete() { future.complete(bytes.toByteArray()); }
    }
}
