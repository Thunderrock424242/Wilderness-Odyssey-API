package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.ai.perf.MemoryStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the local Ollama request and response protocol without network I/O. */
class OllamaChatClientTest {

    @Test
    void buildsNonStreamingChatRequestWithBoundedHistory() {
        List<MemoryStore.ConversationMessage> history = List.of(
                new MemoryStore.ConversationMessage(MemoryStore.Role.PLAYER, "Player", "Hello"),
                new MemoryStore.ConversationMessage(MemoryStore.Role.ASSISTANT, "Aether", "Signal received")
        );

        JsonObject request = OllamaChatClient.buildRequest("llama3.2:latest", "system rules", history, 180);

        assertEquals("llama3.2:latest", request.get("model").getAsString());
        assertFalse(request.get("stream").getAsBoolean());
        assertEquals("60m", request.get("keep_alive").getAsString());
        assertEquals(180, request.getAsJsonObject("options").get("num_predict").getAsInt());
        JsonArray messages = request.getAsJsonArray("messages");
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("Aether: Signal received", messages.get(2).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void acceptsOnlyLoopbackOllamaEndpoints() {
        assertEquals(
                URI.create("http://127.0.0.1:11434/api/chat"),
                OllamaChatClient.resolveChatUri("http://127.0.0.1:11434").orElseThrow()
        );
        assertEquals(
                URI.create("http://localhost:11434/api/chat"),
                OllamaChatClient.resolveChatUri("http://localhost:11434/api").orElseThrow()
        );
        assertTrue(OllamaChatClient.resolveChatUri("https://example.com").isEmpty());
        assertTrue(OllamaChatClient.resolveChatUri("http://192.168.1.20:11434").isEmpty());
        assertTrue(OllamaChatClient.resolveChatUri("not a uri").isEmpty());
    }

    @Test
    void parsesDialogueAndRemovesDuplicateSpeakerPrefix() {
        String response = "{\"message\":{\"role\":\"assistant\",\"content\":\"[Aether] Signal received.\"},\"done\":true}";

        assertEquals(
                "Signal received.",
                OllamaChatClient.parseResponse(response, "Aether", 800).orElseThrow()
        );
        assertTrue(OllamaChatClient.parseResponse("{\"message\":{}}", "Aether", 800).isEmpty());
        assertTrue(OllamaChatClient.parseResponse("not json", "Aether", 800).isEmpty());
    }

    @Test
    void buildsAndParsesStrictJsonVerificationRequest() {
        JsonObject request = OllamaChatClient.buildVerificationRequest("llama3.2:latest", "verify these facts");

        assertEquals("json", request.get("format").getAsString());
        assertEquals(0.0D, request.getAsJsonObject("options").get("temperature").getAsDouble());
        assertEquals(32, request.getAsJsonObject("options").get("num_predict").getAsInt());
        assertEquals(2, request.getAsJsonArray("messages").size());

        String approved = "{\"message\":{\"content\":\"{\\\"approved\\\":true}\"}}";
        String rejected = "{\"message\":{\"content\":\"{\\\"approved\\\":false}\"}}";
        assertTrue(OllamaChatClient.parseVerificationResponse(approved).orElseThrow());
        assertFalse(OllamaChatClient.parseVerificationResponse(rejected).orElseThrow());
        assertTrue(OllamaChatClient.parseVerificationResponse("{\"message\":{\"content\":\"{}\"}}").isEmpty());
    }

    @Test
    void capsOversizedModelDialogue() {
        String longDialogue = "x".repeat(300);

        String cleaned = OllamaChatClient.sanitizeDialogue(longDialogue, "Aether", 128);

        assertEquals(129, cleaned.length());
        assertTrue(cleaned.endsWith("…"));
    }
}
