package com.thunder.wildernessodysseyapi.ai.voice.client;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceInputMode;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceAvailabilityPolicy;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import com.thunder.wildernessodysseyapi.ai.voice.VoicePlaybackQueue;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.PrivateSingleplayerPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client authority for push-to-talk, local-service requests, stale-session
 * rejection, generated audio playback, subtitles, and voice diagnostics.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AetherVoiceClient {
    private static final AetherVoiceClient INSTANCE = new AetherVoiceClient();
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(20);

    private final LocalVoiceServiceClient service = new LocalVoiceServiceClient();
    private final MicrophoneCapture microphone = new MicrophoneCapture();
    private final VoiceAudioPlayer audioPlayer = new VoiceAudioPlayer();
    private final VoicePlaybackQueue playbackQueue = new VoicePlaybackQueue();
    private final AtomicLong lastWarningNanos = new AtomicLong();

    private CompletableFuture<?> activeTranscriptionRequest;
    private CompletableFuture<?> activeSpeechRequest;
    private VoicePlaybackQueue.ScheduledLine activeLine;
    private boolean playbackBusy;
    private boolean pushToTalkWasDown;
    private long lastAetherResponseId;
    private Component subtitle;
    private long subtitleExpiresAtMillis;
    private long playbackRetryAfterNanos;

    private AetherVoiceClient() {
    }

    /** Accepts a server response only when it is newer than prior Aether audio. */
    public static void acceptAetherResponse(long responseId, VoiceLine line) {
        if (line == null || responseId <= INSTANCE.lastAetherResponseId) {
            return;
        }
        INSTANCE.lastAetherResponseId = responseId;
        INSTANCE.replace(line);
    }

    /** Plays authored lore or cinematic text without asking the LLM to rewrite it. */
    public static void speakAuthored(VoiceLine line, boolean queueAfterCurrent) {
        if (line == null) {
            return;
        }
        if (queueAfterCurrent) {
            INSTANCE.enqueue(line);
        } else {
            INSTANCE.replace(line);
        }
    }

    /** Stops generated speech and invalidates every in-flight service request. */
    public static void stopSpeech() {
        INSTANCE.cancelAll();
    }

    /** Returns whether optional voice is enabled inside an unpublished integrated world. */
    public static boolean isVoiceAvailable() {
        return voiceAvailable();
    }

    static SubtitleSnapshot subtitle() {
        Component current = INSTANCE.subtitle;
        if (current == null || System.currentTimeMillis() >= INSTANCE.subtitleExpiresAtMillis) {
            INSTANCE.subtitle = null;
            return new SubtitleSnapshot(null, 0.0F);
        }
        long remaining = INSTANCE.subtitleExpiresAtMillis - System.currentTimeMillis();
        float alpha = Math.min(1.0F, remaining / 500.0F);
        return new SubtitleSnapshot(current, alpha);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        INSTANCE.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.microphone.cancel();
        INSTANCE.cancelTranscription();
        INSTANCE.cancelAll();
        INSTANCE.lastAetherResponseId = 0L;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            INSTANCE.microphone.cancel();
            INSTANCE.cancelTranscription();
            INSTANCE.cancelAll();
        }
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean permitted = isPrivateSingleplayer(minecraft);
        while (AetherVoiceKeyMappings.VOICE_STATUS.consumeClick()) {
            if (permitted) {
                requestStatus(minecraft);
            }
        }
        boolean enabled = VoiceAvailabilityPolicy.permits(AetherVoiceConfig.VOICE_ENABLED.get(), permitted);
        if (!enabled) {
            if (pushToTalkWasDown || microphone.isRecording() || playbackBusy || playbackQueue.pendingCount() > 0) {
                microphone.cancel();
                cancelAll();
            }
            pushToTalkWasDown = false;
            return;
        }

        boolean pushToTalkDown = AetherVoiceConfig.INPUT_MODE.get() == VoiceInputMode.PUSH_TO_TALK
                && minecraft.screen == null
                && AetherVoiceKeyMappings.PUSH_TO_TALK.isDown();
        if (pushToTalkDown && !pushToTalkWasDown) {
            beginPushToTalk(minecraft);
        } else if (!pushToTalkDown && pushToTalkWasDown) {
            endPushToTalk(minecraft);
        }
        pushToTalkWasDown = pushToTalkDown;
        startNextPlayback(minecraft);
    }

    private void beginPushToTalk(Minecraft minecraft) {
        cancelTranscription();
        cancelAll();
        if (!microphone.start()) {
            userWarning(minecraft, "Microphone unavailable: " + microphone.lastError());
            return;
        }
        minecraft.player.displayClientMessage(Component.literal("[Aether Voice] Listening…"), true);
    }

    private void endPushToTalk(Minecraft minecraft) {
        CompletableFuture<String> transcriptionRequest = microphone.stop().thenCompose(wav -> {
            if (wav.length == 0) {
                return CompletableFuture.completedFuture("");
            }
            return service.transcribe(wav);
        });
        activeTranscriptionRequest = transcriptionRequest;
        transcriptionRequest.whenComplete((transcript, failure) -> minecraft.execute(() -> {
            if (activeTranscriptionRequest != transcriptionRequest) {
                return;
            }
            activeTranscriptionRequest = null;
            if (failure != null) {
                userWarning(minecraft, "Transcription unavailable: " + safeFailure(failure));
                return;
            }
            if (transcript == null || transcript.isBlank()) {
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.literal("[Aether Voice] No speech detected."), true);
                }
                return;
            }
            if (minecraft.player == null) {
                return;
            }
            minecraft.player.connection.sendChat(transcript);
        }));
    }

    private void replace(VoiceLine line) {
        if (!voiceAvailable() || !playbackAllowedNow() || line.speechText().isBlank()) {
            return;
        }
        audioPlayer.stop();
        activeLine = null;
        playbackBusy = false;
        playbackQueue.replace(line);
    }

    private void enqueue(VoiceLine line) {
        if (voiceAvailable() && playbackAllowedNow() && !line.speechText().isBlank()) {
            playbackQueue.enqueue(line);
        }
    }

    private void startNextPlayback(Minecraft minecraft) {
        if (playbackBusy || !playbackAllowedNow()) {
            return;
        }
        VoicePlaybackQueue.ScheduledLine scheduled = playbackQueue.poll().orElse(null);
        if (scheduled == null) {
            return;
        }
        playbackBusy = true;
        activeLine = scheduled;
        CompletableFuture<byte[]> speechRequest = service.speak(scheduled.line());
        activeSpeechRequest = speechRequest;
        speechRequest.whenComplete((wav, failure) -> minecraft.execute(() -> {
            if (activeSpeechRequest == speechRequest) {
                activeSpeechRequest = null;
            }
            if (!isActive(scheduled)) {
                return;
            }
            if (failure != null) {
                logWarning("Speech generation unavailable: " + safeFailure(failure));
                suspendPlayback(5);
                return;
            }
            // Cinematics render their server-authored subtitle and timing, so
            // the general voice overlay must not draw a duplicate line.
            if (AetherVoiceConfig.SUBTITLES.get() && !CinematicClientController.get().isActive()) {
                subtitle = Component.literal("[" + scheduled.line().speaker() + "] " + scheduled.line().speechText());
                subtitleExpiresAtMillis = System.currentTimeMillis() + subtitleDuration(scheduled.line().speechText());
            }
            float volume = AetherVoiceConfig.VOICE_VOLUME.get().floatValue()
                    * minecraft.options.getSoundSourceVolume(SoundSource.VOICE)
                    * minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            audioPlayer.play(wav, volume).whenComplete((ignored, playbackFailure) -> minecraft.execute(() -> {
                if (playbackFailure != null) {
                    logWarning("Audio playback unavailable: " + safeFailure(playbackFailure));
                    suspendPlayback(20);
                    return;
                }
                finish(scheduled);
            }));
        }));
    }

    private void finish(VoicePlaybackQueue.ScheduledLine scheduled) {
        if (activeLine != scheduled) {
            return;
        }
        activeLine = null;
        playbackBusy = false;
    }

    private boolean isActive(VoicePlaybackQueue.ScheduledLine scheduled) {
        return activeLine == scheduled && playbackQueue.isCurrent(scheduled);
    }

    private void cancelAll() {
        CompletableFuture<?> request = activeSpeechRequest;
        activeSpeechRequest = null;
        if (request != null) {
            request.cancel(true);
        }
        playbackQueue.cancelAll();
        audioPlayer.stop();
        activeLine = null;
        playbackBusy = false;
        subtitle = null;
        subtitleExpiresAtMillis = 0L;
    }

    private void cancelTranscription() {
        CompletableFuture<?> request = activeTranscriptionRequest;
        activeTranscriptionRequest = null;
        if (request != null) {
            request.cancel(true);
        }
    }

    private void suspendPlayback(int seconds) {
        cancelAll();
        playbackRetryAfterNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    private boolean playbackAllowedNow() {
        return System.nanoTime() >= playbackRetryAfterNanos;
    }

    private void requestStatus(Minecraft minecraft) {
        service.status().whenComplete((status, failure) -> minecraft.execute(() -> {
            if (minecraft.player == null) {
                return;
            }
            if (failure != null) {
                userWarning(minecraft, "Service unavailable: " + safeFailure(failure));
                return;
            }
            String microphoneStatus = MicrophoneCapture.isSupported() ? "Ready" : "Unavailable";
            String stt = status.speechRecognitionReady() ? "Ready" : "Not ready";
            String tts = status.textToSpeechReady() ? "Ready" : "Not ready";
            if (status.textToSpeechReady()) {
                playbackRetryAfterNanos = 0L;
            }
            minecraft.player.displayClientMessage(Component.literal(
                    "[Aether Voice] Microphone: " + microphoneStatus + " | STT: " + stt + " | TTS: " + tts
                            + " | Device: " + status.device() + " | Voice: " + status.voiceModel()
            ), false);
            minecraft.player.displayClientMessage(Component.literal(
                    "[Aether Voice] Models: " + status.modelState()
                            + " | Last latency — STT: " + formatLatency(status.lastSttMilliseconds())
                            + " | TTS: " + formatLatency(status.lastTtsMilliseconds())
                            + (status.lastError().isBlank() ? "" : " | Last error: " + status.lastError())
            ), false);
        }));
    }

    private void userWarning(Minecraft minecraft, String message) {
        if (!shouldWarn()) {
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("[Aether Voice] " + message), false);
        }
        ModConstants.LOGGER.warn("[Aether Voice] {}", message);
    }

    private void logWarning(String message) {
        if (shouldWarn()) {
            ModConstants.LOGGER.warn("[Aether Voice] {}", message);
        }
    }

    private boolean shouldWarn() {
        long now = System.nanoTime();
        long previous = lastWarningNanos.get();
        return (previous == 0L || now - previous >= WARNING_INTERVAL_NANOS)
                && lastWarningNanos.compareAndSet(previous, now);
    }

    private static boolean voiceAvailable() {
        return VoiceAvailabilityPolicy.permits(
                AetherVoiceConfig.VOICE_ENABLED.get(),
                isPrivateSingleplayer(Minecraft.getInstance())
        );
    }

    private static boolean isPrivateSingleplayer(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null && minecraft.hasSingleplayerServer()
                && PrivateSingleplayerPolicy.permits(minecraft.getSingleplayerServer());
    }

    private static String safeFailure(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        String message = cause.getMessage();
        String detail = message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
        return detail.substring(0, Math.min(180, detail.length()));
    }

    private static String formatLatency(double milliseconds) {
        return milliseconds < 0.0D ? "n/a" : Math.round(milliseconds) + " ms";
    }

    private static long subtitleDuration(String text) {
        int words = text == null || text.isBlank() ? 1 : text.trim().split("\\s+").length;
        return Math.max(2_000L, Math.min(12_000L, words * 380L));
    }

    record SubtitleSnapshot(Component text, float alpha) {
    }
}
