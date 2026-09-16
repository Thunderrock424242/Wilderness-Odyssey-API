package com.thunder.wildernessodysseyapi.ai.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual game-side orchestrator with a mocked remote backend. */
class AIClientBackendTest {
    @TempDir Path state;
    private HttpServer gateway;
    private java.util.concurrent.ExecutorService workers;
    private AIClient client;

    @AfterEach
    void stop() {
        if (client != null) {
            client.close();
        }
        if (gateway != null) {
            gateway.stop(0);
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    @Test
    void preservesAuthoredFallbackAfterBackendOutageAndKeepsItDeterministic() throws Exception {
        AIConfig config = bundled();
        config.setBackend(new AIBackendConfig(true, AIBackendConfig.Mode.REMOTE,
                "http://127.0.0.1:1", "test-key-only", "test-server", 1, 1, 0, 30, 2, false));
        client = new AIClient(config, state, () -> true);
        UUID player = UUID.randomUUID();
        AIFallbackResponder.ResponseContext context = new AIFallbackResponder.ResponseContext(
                Set.of("surface", "zone:meteor_site", "lore:project_eden_03"));
        var expected = client.fallback("Aether, show prompts", context);
        var first = client.sendMessageWithVoice("minecraft:overworld", "save-" + player,
                player, "Tester", "Aether, show prompts", context);
        var second = client.sendMessageWithVoice("minecraft:overworld", "save-" + player,
                player, "Tester", "Aether, show prompts", context);
        assertEquals(expected, first);
        assertEquals(first, second);
        assertFalse(first.text().isBlank());
        assertFalse(client.getBackendStatus().reachable());
    }

    @Test
    void keepsPlayersAndSavesSeparateAndNeverSerializesLocalProfileKeys() throws Exception {
        List<JsonObject> requests = startGateway();
        client = new AIClient(configured(), state, () -> true);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        send(first, "save-one-" + first, "Aether, first message");
        send(second, "save-one-" + second, "Aether, second message");
        send(first, "save-two-" + first, "Aether, other save");
        send(first, "save-one-" + first, "Aether, follow-up");
        assertEquals(4, requests.size());
        assertEquals(0, requests.get(0).getAsJsonArray("history").size());
        assertEquals(0, requests.get(1).getAsJsonArray("history").size());
        assertEquals(0, requests.get(2).getAsJsonArray("history").size());
        assertEquals(2, requests.get(3).getAsJsonArray("history").size());
        assertEquals(first.toString(), requests.get(3).get("playerId").getAsString());
        assertTrue(requests.stream().noneMatch(request -> request.toString().contains("save-one")));
        assertTrue(requests.stream().noneMatch(request -> request.toString().contains("test-key-only")));
    }

    @Test
    void localRememberRecallAndForgetStayOffTheBackendByDefault() throws Exception {
        List<JsonObject> requests = startGateway();
        client = new AIClient(configured(), state, () -> true);
        UUID player = UUID.randomUUID();
        String key = "save-" + player;
        send(player, key, "Aether remember I like hiking");
        send(player, key, "Aether what do you remember about me");
        // Control exchanges stay local even if a wording isn't classified as recall:
        // the actual privacy guarantee below also checks no durable profile note is attached.
        send(player, key, "Aether, hello");
        JsonObject last = requests.getLast();
        assertEquals("", last.getAsJsonObject("context").get("playerMemory").getAsString());
        assertFalse(last.getAsJsonArray("history").toString().contains("I'll remember"));
        send(player, key, "Aether forget everything about me");
        send(player, key, "Aether, hello again");
        assertEquals(0, requests.getLast().getAsJsonArray("history").size());
    }

    @Test
    void ignoresUnaddressedChatAndGuardsNetworkFromTheTickThread() throws Exception {
        List<JsonObject> requests = startGateway();
        client = new AIClient(configured(), state, () -> false);
        assertFalse(client.isAiInvocation("Anyone want to build a shelter?"));
        assertFalse(client.isAiInvocation("aetherium is a material"));
        assertTrue(client.isAiInvocation("Aether, hello"));
        assertTrue(client.isAiInvocation("Eclipse, what is this anomaly?"));
        send(UUID.randomUUID(), "save-test", "Aether, hello");
        assertTrue(requests.isEmpty());
    }

    private void send(UUID player, String key, String message) {
        var reply = client.sendMessageWithVoice("minecraft:overworld", key, player, "SameDisplayName",
                message, new AIFallbackResponder.ResponseContext(Set.of("surface", "biome:forest")));
        assertFalse(reply.text().isBlank());
    }

    private AIConfig configured() throws Exception {
        AIConfig config = bundled();
        config.setBackend(new AIBackendConfig(true, AIBackendConfig.Mode.REMOTE,
                "http://127.0.0.1:" + gateway.getAddress().getPort(), "test-key-only",
                "test-server", 3, 1, 0, 30, 2, false));
        return config;
    }

    private static AIConfig bundled() throws Exception {
        try (var input = AIClientBackendTest.class.getClassLoader().getResourceAsStream("ai_config.yaml")) {
            assertNotNull(input);
            return AIConfigLoader.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private List<JsonObject> startGateway() throws Exception {
        List<JsonObject> requests = new CopyOnWriteArrayList<>();
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        workers = Executors.newFixedThreadPool(2);
        gateway.setExecutor(workers);
        gateway.createContext("/v1/aether/generate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            requests.add(request);
            JsonObject response = new JsonObject();
            response.addProperty("requestId", request.get("requestId").getAsString());
            response.addProperty("success", true);
            response.addProperty("speaker", "Aether");
            response.addProperty("response", "Recovered response.");
            response.addProperty("model", "aether-custom:8b");
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            exchange.close();
        });
        gateway.start();
        return requests;
    }
}
