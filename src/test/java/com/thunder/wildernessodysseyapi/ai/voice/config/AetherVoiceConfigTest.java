package com.thunder.wildernessodysseyapi.ai.voice.config;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceInputMode;
import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AetherVoiceConfigTest {
    @Test
    void defaultsToOptionalPushToTalkOnLoopback() {
        WildernessConfigSpecs.initialize();

        assertFalse(AetherVoiceConfig.VOICE_ENABLED.getDefault());
        assertEquals(VoiceInputMode.PUSH_TO_TALK, AetherVoiceConfig.INPUT_MODE.getDefault());
        assertEquals("http://127.0.0.1:8765", AetherVoiceConfig.SERVICE_ENDPOINT.getDefault());
        assertEquals("af_heart", AetherVoiceConfig.VOICE_NAME.getDefault());
        assertEquals(0.96D, AetherVoiceConfig.SPEECH_SPEED.getDefault());
        assertTrue(AetherVoiceConfig.SUBTITLES.getDefault());
        assertEquals(List.of("aether_voice", "enabled"), AetherVoiceConfig.VOICE_ENABLED.getPath());
    }
}
