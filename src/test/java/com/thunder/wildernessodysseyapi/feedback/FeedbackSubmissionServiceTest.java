package com.thunder.wildernessodysseyapi.feedback;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.playtest.LocalWebhook;
import com.thunder.wildernessodysseyapi.playtest.PlaytestReplies;
import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackSubmissionServiceTest {
    private final FeedbackSubmissionService service = new FeedbackSubmissionService();

    private static FeedbackConfig.FeedbackConfigValues config(String endpoint) {
        return new FeedbackConfig.FeedbackConfigValues(true, endpoint, 2, 2000, 30);
    }

    @Test
    void successWaitsForHttpAndServerExecutor() throws Exception {
        try (var endpoint = new LocalWebhook(204, "", true)) {
            var replies = new ArrayList<PlaytestWebhookClient.Result>();
            var serverTasks = new LinkedBlockingQueue<Runnable>();
            var request = service.submit("Player", "uuid", "@everyone " + "x".repeat(1990), config(endpoint.endpoint()));
            var reply = PlaytestReplies.deliver(request, serverTasks::add, replies::add);
            assertTrue(endpoint.received.await(2, TimeUnit.SECONDS));
            assertFalse(request.isDone());
            assertTrue(serverTasks.isEmpty());
            assertTrue(replies.isEmpty());
            endpoint.release.countDown();
            Runnable callback = serverTasks.poll(4, TimeUnit.SECONDS);
            assertNotNull(callback);
            assertTrue(replies.isEmpty());
            callback.run();
            reply.get(1, TimeUnit.SECONDS);
            assertEquals(java.util.List.of(DELIVERED), replies);
            var payload = JsonParser.parseString(endpoint.request).getAsJsonObject();
            assertTrue(payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
            assertEquals(2000, payload.getAsJsonArray("embeds").get(0).getAsJsonObject().get("description").getAsString().length());
        }
    }

    @Test
    void reportsHttpFailureAndMalformedResponse() throws Exception {
        try (var failed = new LocalWebhook(500, "error", false);
             var malformed = new LocalWebhook(200, "{broken", false)) {
            assertEquals(UNAVAILABLE, service.submit("Player", "uuid", "feedback", config(failed.endpoint())).get(4, TimeUnit.SECONDS));
            assertEquals(INVALID_RESPONSE, service.submit("Player", "uuid", "feedback", config(malformed.endpoint())).get(4, TimeUnit.SECONDS));
        }
    }

    @Test
    void networkExceptionNeverBecomesSuccess() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        assertEquals(UNAVAILABLE, service.submit("Player", "uuid", "feedback",
                config("http://127.0.0.1:" + port)).get(4, TimeUnit.SECONDS));
        var replies = new ArrayList<PlaytestWebhookClient.Result>();
        PlaytestReplies.deliver(CompletableFuture.failedFuture(new java.io.IOException("private")),
                Runnable::run, replies::add).join();
        assertEquals(java.util.List.of(UNAVAILABLE), replies);
    }
}