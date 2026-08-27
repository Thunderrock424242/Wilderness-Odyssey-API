package com.thunder.wildernessodysseyapi.ai.voice.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the in-memory capture envelope without requiring microphone hardware. */
class MicrophoneCaptureTest {
    @Test
    void writesStandardLittleEndianMonoWavHeader() {
        byte[] pcm = {1, 2, 3, 4};

        byte[] wav = MicrophoneCapture.wav(pcm);

        assertEquals(48, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        assertArrayEquals(pcm, java.util.Arrays.copyOfRange(wav, 44, 48));
    }
}
