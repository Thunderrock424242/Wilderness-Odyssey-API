package com.thunder.wildernessodysseyapi.cinematic.client;

import com.mojang.text2speech.Narrator;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import com.thunder.wildernessodysseyapi.ai.voice.client.AetherVoiceClient;
import com.thunder.wildernessodysseyapi.ai.voice.client.VoiceAudioPlayer;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.PrivateSingleplayerPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bridges authored cinematic cues into the shared local Aether voice session. */
public final class AetherCinematicVoice {
    private static final int MAX_CLIP_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CACHED_CLIPS = 32;
    private static final VoiceAudioPlayer AUTHORED_CLIP_PLAYER = new VoiceAudioPlayer();
    private static final Map<ResourceLocation, byte[]> AUTHORED_CLIPS = new LinkedHashMap<>();
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

    /** Preloads deterministic cinematic WAVs during a sequence's black-screen lead-in. */
    public static void preloadAuthoredClips(Collection<ResourceLocation> clipIds) {
        if (!AetherVoiceConfig.CINEMATIC_NARRATION.get() || !isPrivateSingleplayer() || clipIds == null) {
            return;
        }
        for (ResourceLocation clipId : clipIds) {
            if (clipId != null) {
                loadAuthoredClip(clipId);
            }
        }
    }

    /** Plays one bundled authored clip immediately, bypassing live synthesis and OS Narrator. */
    public static void playAuthoredClip(ResourceLocation clipId) {
        if (!AetherVoiceConfig.CINEMATIC_NARRATION.get() || !isPrivateSingleplayer() || clipId == null) {
            stop();
            return;
        }
        byte[] wav = loadAuthoredClip(clipId);
        if (wav == null) {
            return;
        }
        AetherVoiceClient.stopSpeech();
        clearNarratorFallback();
        Minecraft minecraft = Minecraft.getInstance();
        float volume = AetherVoiceConfig.VOICE_VOLUME.get().floatValue()
                * minecraft.options.getSoundSourceVolume(SoundSource.VOICE)
                * minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        AUTHORED_CLIP_PLAYER.play(wav, volume).whenComplete((ignored, failure) -> {
            if (failure != null) {
                ModConstants.LOGGER.warn("[Aether Voice] Authored cinematic clip {} failed: {}",
                        clipId, failure.getClass().getSimpleName());
            }
        });
    }

    /** Cancels queued/generated cinematic speech when the presentation ends. */
    public static void stop() {
        AetherVoiceClient.stopSpeech();
        AUTHORED_CLIP_PLAYER.stop();
        clearNarratorFallback();
    }

    private static byte[] loadAuthoredClip(ResourceLocation clipId) {
        synchronized (AUTHORED_CLIPS) {
            byte[] cached = AUTHORED_CLIPS.get(clipId);
            if (cached != null) {
                return cached;
            }
            if (AUTHORED_CLIPS.size() >= MAX_CACHED_CLIPS) {
                ModConstants.LOGGER.warn("[Aether Voice] Authored clip cache limit reached; skipped {}", clipId);
                return null;
            }
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(clipId).orElse(null);
            if (resource == null) {
                ModConstants.LOGGER.warn("[Aether Voice] Missing authored cinematic clip {}", clipId);
                return null;
            }
            try (InputStream input = resource.open()) {
                byte[] wav = input.readNBytes(MAX_CLIP_BYTES + 1);
                if (wav.length == 0 || wav.length > MAX_CLIP_BYTES) {
                    ModConstants.LOGGER.warn("[Aether Voice] Invalid authored cinematic clip size for {}", clipId);
                    return null;
                }
                AUTHORED_CLIPS.put(clipId, wav);
                return wav;
            } catch (IOException exception) {
                ModConstants.LOGGER.warn("[Aether Voice] Could not load authored cinematic clip {}: {}",
                        clipId, exception.getClass().getSimpleName());
                return null;
            }
        }
    }

    private static void clearNarratorFallback() {
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
