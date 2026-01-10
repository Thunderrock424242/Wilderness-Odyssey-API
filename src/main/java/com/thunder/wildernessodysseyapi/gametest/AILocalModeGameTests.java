package com.thunder.wildernessodysseyapi.gametest;

import com.thunder.wildernessodysseyapi.AI.AI_story.LocalModelClient;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class AILocalModeGameTests {
    private static final String BATCH = "ai";

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void localModelConnects(GameTestHelper helper) {
        HttpServer server;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            executor.shutdownNow();
            helper.fail("Failed to start local AI test server: " + e.getMessage());
            return;
        }

        server.setExecutor(executor);
        server.createContext("/api/generate", exchange -> {
            byte[] response = "{\"response\":\"Test reply\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        LocalModelClient client = new LocalModelClient(baseUrl, "test-model", Duration.ofSeconds(2));

        helper.runAtTickTime(1, () -> {
            try {
                Optional<String> reply = client.generateReply("system", "hello", "context");
                helper.assertTrue(reply.isPresent(), "Local AI client did not return a response.");
                helper.assertTrue("Test reply".equals(reply.orElse("")), "Local AI client response was unexpected.");
                helper.succeed();
            } finally {
                server.stop(0);
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
}
