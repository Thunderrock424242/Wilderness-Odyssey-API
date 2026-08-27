package com.thunder.wildernessodysseyapi.cinematic.client;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import com.thunder.wildernessodysseyapi.ai.voice.client.AetherVoiceClient;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import net.minecraft.network.chat.Component;

/** Bridges authored cinematic cues into the shared local Aether voice session. */
public final class AetherCinematicVoice {
    private AetherCinematicVoice() {
    }

    /** Queues one deterministic line; the cinematic subtitle remains authoritative on failure. */
    public static void speak(Component text) {
        speak(text, VoiceEmotion.DAMAGED, 0.10F);
    }

    /** Queues an authored cue with explicit, bounded delivery metadata for future sequences. */
    public static void speak(Component text, VoiceEmotion emotion, float radioEffect) {
        if (text == null || text.getString().isBlank() || !AetherVoiceConfig.CINEMATIC_NARRATION.get()) {
            return;
        }
        AetherVoiceClient.speakAuthored(
                VoiceLine.authored(
                        "Aether",
                        text.getString(),
                        text.getString(),
                        emotion,
                        radioEffect
                ),
                true
        );
    }

    /** Cancels queued/generated cinematic speech when the presentation ends. */
    public static void stop() {
        AetherVoiceClient.stopSpeech();
    }
}
