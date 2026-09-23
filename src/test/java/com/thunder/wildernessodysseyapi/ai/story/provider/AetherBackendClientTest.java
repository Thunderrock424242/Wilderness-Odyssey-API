package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thunder.wildernessodysseyapi.ai.story.AIBackendConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP boundary regressions with an in-process gateway double, never real Ollama. */
class AetherBackendClientTest {
    private HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(6);
    private final List<AetherBackendClient> clients = new ArrayList<>();

    @AfterEach
    void close() {
        clients.forEach(AetherBackendClient::close);
        if (server != null) {
            server.stop(0);
        }
        workers.shutdownNow();
    }

    @Test
    void serializesStructuredContextAndParsesVerifiedVoiceResponse() throws Exception {
        AtomicReference<JsonObject> captured = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        start(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonObject request = read(exchange);
            captured.set(request);
            send(exchange, 200, success(request));
        });
        AetherRequest request = request("test-one", UUID.randomUUID());
        var reply = client(5, 3).generate(request, List.of("Aether", "Eclipse"), 800);
        assertTrue(reply.successful());
        assertEquals("Aether", reply.speaker());
        assertEquals("Recovered.", reply.displayText());
        assertEquals("calm", reply.emotion().name().toLowerCase(java.util.Locale.ROOT));
        assertNull(authorization.get());
        assertEquals("minecraft:overworld", captured.get().getAsJsonObject("context").get("dimension").getAsString());
        assertEquals("test-one", captured.get().get("serverId").getAsString());
        assertFalse(captured.get().has("apiKey"));
        assertFalse(captured.get().has("systemPrompt"));
        assertEquals(1, captured.get().getAsJsonArray("history").size());
    }

    @Test
    void healthAndReadinessAreCheckedWithoutAuthorizationHeaders() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        start(exchange -> send(exchange, 500, "{}"));
        for (String path : List.of("/health", "/ready")) {
            server.createContext(path, exchange -> {
                checks.incrementAndGet();
                String header = exchange.getRequestHeaders().getFirst("Authorization");
                if (header != null) { authorization.set(header); }
                send(exchange, 200, "{\"status\":\"UP\",\"ready\":true,\"model\":\"aether-custom:8b\"}");
            });
        }
        var status = client(3, 1).checkHealth();
        assertTrue(status.reachable());
        assertTrue(status.modelReady());
        assertEquals("aether-custom:8b", status.model());
        assertEquals(2, checks.get());
        assertNull(authorization.get());
    }

    @Test
    void hostingProxyAccessDenialIsNotRetriedAndOpensCircuit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            send(exchange, 401, "{\"success\":false,\"error\":\"UNAUTHORIZED\"}");
        });
        AetherBackendClient client = client(3, 3);
        assertEquals("UNAUTHORIZED", client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).error());
        assertEquals("CIRCUIT_OPEN", client.generate(request("two", UUID.randomUUID()), List.of("Aether"), 800).error());
        assertEquals(1, calls.get());
    }

    @Test
    void retriesExplicitTransientGatewayFailureWithinOneRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            JsonObject body = read(exchange);
            if (calls.incrementAndGet() < 3) {
                send(exchange, 502, "{}");
            } else {
                send(exchange, 200, success(body));
            }
        });
        assertTrue(client(3, 3).generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        assertEquals(3, calls.get());
    }

    @Test
    void circuitRecoversWithASingleProbeAfterCooldown() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            JsonObject body = read(exchange);
            if (calls.incrementAndGet() == 1) {
                send(exchange, 503, "{}");
            } else {
                send(exchange, 200, success(body));
            }
        });
        AtomicLong clock = new AtomicLong(System.nanoTime());
        var client = new AetherBackendClient(config(2, 1), () -> true, clock::get);
        clients.add(client);
        assertFalse(client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        assertEquals("CIRCUIT_OPEN", client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).error());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(31));
        assertTrue(client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        assertEquals(2, calls.get());
    }

    @Test
    void timeoutBoundsSlowResponseBodyWithoutRetryingInference() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(1800);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        assertTimeoutPreemptively(Duration.ofMillis(1600), () ->
                assertEquals("TIMEOUT", client(1, 3).generate(
                        request("one", UUID.randomUUID()), List.of("Aether"), 800).error()));
        assertEquals(1, calls.get());
    }

    @Test
    void stoppedBackendFailsGracefullyAndBoundedCircuitSkipsFurtherCalls() throws Exception {
        start(exchange -> send(exchange, 200, "{}"));
        AetherBackendClient client = client(1, 1);
        server.stop(0);
        server = null;
        assertFalse(client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        assertEquals("CIRCUIT_OPEN", client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).error());
    }

    @Test
    void mainThreadGuardPreventsAllNetworkOperations() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{}");
        });
        var client = new AetherBackendClient(config(1, 1), () -> false);
        clients.add(client);
        assertFalse(client.generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        client.checkHealth();
        assertEquals(0, calls.get());
    }

    @Test
    void overloadAndOversizedOrMismatchedResponsesNeverReachChat() throws Exception {
        start(exchange -> send(exchange, 200, "x".repeat(70_000)));
        assertFalse(client(2, 1).generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        AetherRequest request = request("one", UUID.randomUUID());
        JsonObject reply = JsonParser.parseString(success(new com.google.gson.Gson().toJsonTree(request).getAsJsonObject())).getAsJsonObject();
        reply.addProperty("requestId", UUID.randomUUID().toString());
        assertTrue(AetherBackendClient.parseResponse(reply.toString().getBytes(StandardCharsets.UTF_8),
                request, List.of("Aether"), 800).isEmpty());
        reply.addProperty("requestId", request.requestId());
        reply.addProperty("speaker", "Unknown");
        assertTrue(AetherBackendClient.parseResponse(reply.toString().getBytes(StandardCharsets.UTF_8),
                request, List.of("Aether"), 800).isEmpty());
    }

    @Test
    void healthAndReadinessDescribeGatewayAndModelSeparately() throws Exception {
        start(exchange -> send(exchange, 200, "{}"));
        server.createContext("/health", exchange -> send(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/ready", exchange -> send(exchange, 503,
                "{\"status\":\"DOWN\",\"model\":\"aether-custom:8b\"}"));
        BackendStatus status = client(2, 1).checkHealth();
        assertTrue(status.reachable());
        assertFalse(status.modelReady());
        assertEquals("aether-custom:8b", status.model());
    }

    @Test
    void simultaneousPlayersStayBoundedWithoutBlockingCallerOnSaturation() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        start(exchange -> {
            JsonObject body = read(exchange);
            started.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
                send(exchange, 200, success(body));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        AetherBackendClient client = client(4, 1);
        ExecutorService players = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = players.submit(() -> assertTrue(client.generate(
                    request("server-a", UUID.randomUUID()), List.of("Aether"), 800).successful()));
            Future<?> second = players.submit(() -> assertTrue(client.generate(
                    request("server-b", UUID.randomUUID()), List.of("Aether"), 800).successful()));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofMillis(300), () ->
                    assertEquals("CLIENT_BUSY", client.generate(
                            request("server-c", UUID.randomUUID()), List.of("Aether"), 800).error()));
            release.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            players.shutdownNow();
        }
    }

    @Test
    void endpointRejectsEmbeddedSecretsAndRedirectIsNotFollowed() throws Exception {
        assertTrue(AetherBackendClient.baseUri("http://user:secret@example.com").isEmpty());
        assertTrue(AetherBackendClient.baseUri("https://example.com?api_key=secret").isEmpty());
        assertTrue(AetherBackendClient.baseUri("http://127.0.0.1:11434/api").isEmpty());
        assertTrue(AetherBackendClient.baseUri("https://example.com/aether").isPresent());
        AtomicInteger followed = new AtomicInteger();
        start(exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirected");
            send(exchange, 307, "{}");
        });
        server.createContext("/redirected", exchange -> {
            followed.incrementAndGet();
            send(exchange, 200, "{}");
        });
        assertFalse(client(2, 1).generate(request("one", UUID.randomUUID()), List.of("Aether"), 800).successful());
        assertEquals(0, followed.get());
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        server.setExecutor(workers);
        server.createContext("/v1/aether/generate", handler);
        server.start();
    }

    private AetherBackendClient client(int seconds, int attempts) {
        AetherBackendClient client = new AetherBackendClient(config(seconds, attempts));
        clients.add(client);
        return client;
    }

    private AIBackendConfig config(int seconds, int attempts) {
        return new AIBackendConfig(true, AIBackendConfig.Mode.REMOTE,
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-server",
                seconds, attempts, 1, 30, 2, false);
    }

    private static AetherRequest request(String serverId, UUID playerId) {
        return new AetherRequest(UUID.randomUUID().toString(), serverId, "minecraft:overworld",
                playerId.toString(), "TestPlayer", "Aether", "Aether, what is this?",
                new AetherRequest.Context("minecraft:overworld", "forest", true, "server_chat",
                        List.of("surface", "zone:meteor_site", "lore:project_eden_03"), ""),
                List.of(new AetherRequest.HistoryMessage("assistant", "Aether", "Prior response.")));
    }

    private static JsonObject read(HttpExchange exchange) throws IOException {
        return JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String success(JsonObject request) {
        JsonObject reply = new JsonObject();
        reply.addProperty("requestId", request.get("requestId").getAsString());
        reply.addProperty("success", true);
        reply.addProperty("speaker", "Aether");
        reply.addProperty("response", "Recovered.");
        reply.addProperty("speech", "Recovered.");
        reply.addProperty("emotion", "calm");
        reply.addProperty("model", "aether-custom:8b");
        reply.addProperty("processingTimeMs", 8);
        return reply.toString();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
