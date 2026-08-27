package com.thunder.wildernessodysseyapi.ai.voice.client;

import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that microphone and generated speech can never be sent to a remote host. */
class LocalVoiceServiceClientTest {
    @Test
    void acceptsOnlyPlainHttpLoopbackRoots() {
        assertEquals(
                URI.create("http://127.0.0.1:8765/v1/speak"),
                LocalVoiceServiceClient.resolvePath("http://127.0.0.1:8765", "/v1/speak").orElseThrow()
        );
        assertEquals(
                URI.create("http://localhost:8765/v1/status"),
                LocalVoiceServiceClient.resolvePath("http://localhost:8765/", "/v1/status").orElseThrow()
        );
        assertTrue(LocalVoiceServiceClient.resolvePath("https://127.0.0.1:8765", "/v1/speak").isEmpty());
        assertTrue(LocalVoiceServiceClient.resolvePath("http://192.168.1.40:8765", "/v1/speak").isEmpty());
        assertTrue(LocalVoiceServiceClient.resolvePath("http://example.com", "/v1/speak").isEmpty());
    }

    @Test
    void boundsTranscriptionToVanillaChatPacketLength() {
        String transcript = LocalVoiceServiceClient.boundTranscript("x".repeat(400));

        assertEquals(SharedConstants.MAX_CHAT_LENGTH, transcript.length());
    }
}
