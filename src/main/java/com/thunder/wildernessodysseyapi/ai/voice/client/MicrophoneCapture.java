package com.thunder.wildernessodysseyapi.ai.voice.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Captures one bounded 16 kHz mono push-to-talk clip on a dedicated daemon worker. */
final class MicrophoneCapture {
    private static final int SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;
    private static final int MAX_RAW_BYTES = BYTES_PER_SECOND * 30;
    private static final int MIN_RAW_BYTES = BYTES_PER_SECOND / 5;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Aether-Microphone-Capture");
        thread.setDaemon(true);
        return thread;
    });
    private volatile TargetDataLine line;
    private volatile boolean recording;
    private volatile long generation;
    private CompletableFuture<byte[]> captureFuture;
    private volatile String lastError = "";

    synchronized boolean start() {
        if (recording) {
            return false;
        }
        try {
            TargetDataLine opened = AudioSystem.getTargetDataLine(FORMAT);
            opened.open(FORMAT, BYTES_PER_SECOND);
            opened.start();
            line = opened;
            recording = true;
            lastError = "";
            long captureGeneration = ++generation;
            captureFuture = CompletableFuture.supplyAsync(() -> capture(opened, captureGeneration), executor);
            return true;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException exception) {
            lastError = exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage());
            line = null;
            recording = false;
            return false;
        }
    }

    synchronized CompletableFuture<byte[]> stop() {
        if (captureFuture == null) {
            return CompletableFuture.completedFuture(new byte[0]);
        }
        recording = false;
        TargetDataLine active = line;
        line = null;
        if (active != null && active.isOpen()) {
            active.stop();
            active.close();
        }
        CompletableFuture<byte[]> completedCapture = captureFuture;
        captureFuture = null;
        return completedCapture.thenApply(raw -> raw.length < MIN_RAW_BYTES ? new byte[0] : wav(raw));
    }

    synchronized void cancel() {
        recording = false;
        generation++;
        captureFuture = null;
        TargetDataLine active = line;
        line = null;
        if (active != null) {
            active.stop();
            active.close();
        }
    }

    boolean isRecording() {
        return recording;
    }

    String lastError() {
        return lastError;
    }

    static boolean isSupported() {
        return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT));
    }

    private byte[] capture(TargetDataLine active, long captureGeneration) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(BYTES_PER_SECOND * 4);
        byte[] buffer = new byte[4_096];
        try {
            while (isCurrentCapture(active, captureGeneration) && output.size() < MAX_RAW_BYTES) {
                int count = active.read(buffer, 0, Math.min(buffer.length, MAX_RAW_BYTES - output.size()));
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
        } catch (RuntimeException exception) {
            if (isCurrentCapture(active, captureGeneration)) {
                lastError = exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage());
            }
        } finally {
            finishCapture(active, captureGeneration);
            if (active.isOpen()) {
                active.stop();
                active.close();
            }
        }
        return output.toByteArray();
    }

    private boolean isCurrentCapture(TargetDataLine active, long captureGeneration) {
        return recording && generation == captureGeneration && line == active;
    }

    private synchronized void finishCapture(TargetDataLine active, long captureGeneration) {
        if (generation == captureGeneration && line == active) {
            recording = false;
            line = null;
        }
    }

    static byte[] wav(byte[] pcm) {
        int dataLength = pcm == null ? 0 : pcm.length;
        ByteArrayOutputStream output = new ByteArrayOutputStream(dataLength + 44);
        writeAscii(output, "RIFF");
        writeInt(output, dataLength + 36);
        writeAscii(output, "WAVEfmt ");
        writeInt(output, 16);
        writeShort(output, 1);
        writeShort(output, 1);
        writeInt(output, SAMPLE_RATE);
        writeInt(output, BYTES_PER_SECOND);
        writeShort(output, 2);
        writeShort(output, 16);
        writeAscii(output, "data");
        writeInt(output, dataLength);
        if (pcm != null) {
            output.writeBytes(pcm);
        }
        return output.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) {
        for (int index = 0; index < value.length(); index++) {
            output.write(value.charAt(index));
        }
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write(value >>> 8 & 0xFF);
        output.write(value >>> 16 & 0xFF);
        output.write(value >>> 24 & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write(value >>> 8 & 0xFF);
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "unavailable" : message.substring(0, Math.min(160, message.length()));
    }
}
