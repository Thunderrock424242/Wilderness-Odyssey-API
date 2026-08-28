package com.thunder.wildernessodysseyapi.ai.voice.client;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Plays one bounded generated or bundled WAV without touching the render thread. */
public final class VoiceAudioPlayer {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Aether-Voice-Playback");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Clip activeClip;

    /** Replaces any active clip and plays one complete in-memory WAV. */
    public CompletableFuture<Void> play(byte[] wav, float volume) {
        return CompletableFuture.runAsync(() -> playBlocking(wav, volume), executor);
    }

    /** Stops the active clip immediately; safe to call from cinematic cleanup. */
    public void stop() {
        Clip clip = activeClip;
        activeClip = null;
        if (clip != null) {
            clip.stop();
            clip.flush();
            clip.close();
        }
    }

    private void playBlocking(byte[] wav, float volume) {
        stop();
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            Clip clip = AudioSystem.getClip();
            activeClip = clip;
            CountDownLatch completed = new CountDownLatch(1);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                    completed.countDown();
                }
            });
            clip.open(stream);
            applyVolume(clip, volume);
            long maximumSeconds = Math.max(5L, Math.min(120L, clip.getMicrosecondLength() / 1_000_000L + 5L));
            clip.start();
            completed.await(maximumSeconds, TimeUnit.SECONDS);
            clip.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("voice playback interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("voice playback failed: " + exception.getClass().getSimpleName(), exception);
        } finally {
            activeClip = null;
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float bounded = Math.max(0.0001F, Math.min(1.0F, volume));
        float decibels = (float) (20.0D * Math.log10(bounded));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }
}
