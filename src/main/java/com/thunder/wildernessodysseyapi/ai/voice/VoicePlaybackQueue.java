package com.thunder.wildernessodysseyapi.ai.voice;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Small synchronized ordering and cancellation authority for generated audio.
 *
 * <p>Aether replies and manually selected lore replace stale speech. Consecutive
 * authored cinematic cues may queue within the current generation. A completed
 * background request must still pass {@link #isCurrent(ScheduledLine)} before
 * its audio is allowed to play.</p>
 */
public final class VoicePlaybackQueue {
    private static final int MAX_PENDING_LINES = 12;

    private final ArrayDeque<ScheduledLine> pending = new ArrayDeque<>();
    private long generation;
    private long nextSequence;

    /** Cancels older work and schedules the newest player-facing response. */
    public synchronized ScheduledLine replace(VoiceLine line) {
        generation++;
        pending.clear();
        return add(line);
    }

    /** Queues one authored continuation without allowing unbounded narration. */
    public synchronized Optional<ScheduledLine> enqueue(VoiceLine line) {
        if (pending.size() >= MAX_PENDING_LINES) {
            return Optional.empty();
        }
        if (generation == 0L) {
            generation = 1L;
        }
        return Optional.of(add(line));
    }

    public synchronized Optional<ScheduledLine> poll() {
        return Optional.ofNullable(pending.pollFirst());
    }

    public synchronized boolean isCurrent(ScheduledLine line) {
        return line != null && line.generation() == generation;
    }

    public synchronized void cancelAll() {
        generation++;
        pending.clear();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private ScheduledLine add(VoiceLine line) {
        ScheduledLine scheduled = new ScheduledLine(generation, ++nextSequence, line);
        pending.addLast(scheduled);
        return scheduled;
    }

    /** Immutable token checked after every asynchronous service and playback boundary. */
    public record ScheduledLine(long generation, long sequence, VoiceLine line) {
        public ScheduledLine {
            if (line == null) {
                throw new IllegalArgumentException("Voice line is required");
            }
        }
    }
}
