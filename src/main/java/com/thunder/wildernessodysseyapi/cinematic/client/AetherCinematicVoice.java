package com.thunder.wildernessodysseyapi.cinematic.client;

import com.mojang.text2speech.Narrator;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import com.thunder.wildernessodysseyapi.ai.voice.client.AetherVoiceClient;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import com.thunder.wildernessodysseyapi.core.PrivateSingleplayerPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Bridges authored cinematic cues into the shared local Aether voice session. */
public final class AetherCinematicVoice {
    private static boolean narratorFallbackActive;

    private AetherCinematicVoice() {
    }

    /** Queues one deterministic line; the cinematic subtitle remains authoritative on failure. */
    public static void speak(Component text) {
        speak(text, VoiceEmotion.DAMAGED, 0.10F);
    }

    /** Queues an authored cue with explicit, bounded delivery metadata for future sequences. */
    public static void speak(Component text, VoiceEmotion emotion, float radioEffect) {
        if (text == null || text.getString().isBlank()) {
            return;
        }
        if (!AetherVoiceConfig.CINEMATIC_NARRATION.get()) {
            stop();
            return;
        }
        if (!isPrivateSingleplayer()) {
            stop();
            return;
        }
        if (!AetherVoiceClient.isVoiceAvailable()) {
            // Cinematic narration remains audible with no separately started
            // service. This is authored text only; it never invokes the LLM.
            AetherVoiceClient.stopSpeech();
            Narrator.getNarrator().say(text.getString(), false);
            narratorFallbackActive = true;
            return;
        }
        if (narratorFallbackActive) {
            Narrator.getNarrator().clear();
            narratorFallbackActive = false;
        }
        AetherVoiceClient.speakAuthored(
                VoiceLine.authored(
                        "A.E.T.H.E.R",
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
        if (narratorFallbackActive) {
            Narrator.getNarrator().clear();
        }
        narratorFallbackActive = false;
    }

    private static boolean isPrivateSingleplayer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hasSingleplayerServer()
                && PrivateSingleplayerPolicy.permits(minecraft.getSingleplayerServer());
    }
}
