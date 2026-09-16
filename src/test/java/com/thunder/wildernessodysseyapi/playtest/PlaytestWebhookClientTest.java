package com.thunder.wildernessodysseyapi.playtest;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import static com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaytestWebhookClientTest {
    private final PlaytestWebhookClient client = new PlaytestWebhookClient();

    @Test
    void discordRequestsWaitForSavedMessageAndPreserveThread() {
        assertEquals("thread_id=2&wait=true", PlaytestWebhookClient.deliveryUri(
                "https://discord.com/api/webhooks/1/test-only?thread_id=2&wait=false").getRawQuery());
        assertEquals("wait=true", PlaytestWebhookClient.deliveryUri(
                "https://discord.com/api/webhooks/1/test-only").getRawQuery());
        assertNull(PlaytestWebhookClient.deliveryUri("http://127.0.0.1/").getRawQuery());
    }
    @Test
    void validatesConfigurationWithoutNetwork() {
        for (String endpoint : new String[]{"", "invalid", "file:///secret", "http://user:secret@localhost/", "https://example.invalid/#secret"}) {
            assertFalse(PlaytestWebhookClient.isConfigured(endpoint));
            assertEquals(NOT_CONFIGURED, client.post(endpoint, 1, new JsonObject()).join());
        }
    }

    @Test
    void distinguishesReceiptRejectionAndMalformedAcknowledgement() {
        assertEquals(DELIVERED, PlaytestWebhookClient.interpret(204, ""));
        assertEquals(DELIVERED, PlaytestWebhookClient.interpret(200, "{\"id\":\"123456\"}"));
        assertEquals(DELIVERED, PlaytestWebhookClient.interpret(200, "{\"accepted\":true}"));
        assertEquals(REJECTED, PlaytestWebhookClient.interpret(200, "{\"success\":false}"));
        for (String body : new String[]{"", "<html>proxy error</html>", "null", "[]", "{}", "{\"success\":\"true\"}"}) {
            assertEquals(INVALID_RESPONSE, PlaytestWebhookClient.interpret(200, body));
        }
        assertEquals(REJECTED, PlaytestWebhookClient.interpret(410, ""));
        assertEquals(NOT_CONFIGURED, PlaytestWebhookClient.interpret(403, ""));
        assertEquals(RATE_LIMITED, PlaytestWebhookClient.interpret(429, ""));
        assertEquals(UNAVAILABLE, PlaytestWebhookClient.interpret(503, ""));
    }

    @Test
    void rejectsOversizedResponse() throws Exception {
        try (var endpoint = new LocalWebhook(200, "x".repeat(20_000), false)) {
            assertEquals(UNAVAILABLE, client.post(endpoint.endpoint(), 3, new JsonObject()).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void timesOutWithoutWaitingOnCallerThread() throws Exception {
        try (var endpoint = new LocalWebhook(204, "", true)) {
            var future = client.post(endpoint.endpoint(), 1, new JsonObject());
            assertTrue(endpoint.received.await(2, TimeUnit.SECONDS));
            assertEquals(UNAVAILABLE, future.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void reportsConnectionFailure() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        assertEquals(UNAVAILABLE, client.post("http://127.0.0.1:" + port, 1, new JsonObject()).get(3, TimeUnit.SECONDS));
    }
}